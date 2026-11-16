#include "android_diagnostics.h"
#include "frame_sample_window.h"

#if defined(MCLA_ADRENO_DIAGNOSTIC_APK)
#include "vehicle_telemetry.h"
#include <atomic>
#include <cmath>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>
#include <vector>

#include <rex/logging.h>
#include <rex/runtime.h>
#include <rex/ui/presenter.h>

namespace mcla::diagnostics {
namespace {
using Clock = std::chrono::steady_clock;

struct Pulse {
  std::atomic<uint64_t> count{0};
  std::atomic<uint64_t> last_ns{0};

  void Record(uint64_t timestamp_ns) {
    last_ns.store(timestamp_ns, std::memory_order_relaxed);
    count.fetch_add(1, std::memory_order_relaxed);
  }
};

Pulse updates, swaps, ui_paints;
FrameSampleWindow swap_intervals, update_intervals;
std::atomic<uint64_t> start_ns{0}, camera_requests{0}, last_camera_log_ns{0};
std::atomic<uint32_t> player_car{0};

void Stage(const char* event, uint64_t value = 0) {
  const uint64_t now = uint64_t(std::chrono::duration_cast<std::chrono::nanoseconds>(
      Clock::now().time_since_epoch()).count());
  const uint64_t start = start_ns.load(std::memory_order_relaxed);
  REXLOG_INFO("transition: event={} monotonic_us={} since_start_ms={} value={:#x}",
              event, now / 1000, start && now >= start ? int64_t((now - start) / 1000000) : -1,
              value);
}
std::mutex stop_mutex;
std::condition_variable stop_cv;
bool stopping = false;
std::thread worker;

uint64_t NowNs() {
  return uint64_t(std::chrono::duration_cast<std::chrono::nanoseconds>(
                      Clock::now().time_since_epoch()).count());
}

int64_t AgeMs(uint64_t now, uint64_t last) {
  return last && now >= last ? int64_t((now - last) / 1'000'000) : -1;
}

void Run() {
  uint64_t previous_updates = 0, previous_swaps = 0, previous_ui = 0;
  uint64_t swap_cursor = 0, update_cursor = 0;
  uint32_t previous_hud_updates = 0;
  const uint64_t started = NowNs();
  uint64_t previous_report_ns = started;
  REXLOG_INFO("A730 diagnostic: watchdog started (5s; -1 age means no event yet); "
              "no driver synchronization changes");
  for (;;) {
    {
      std::unique_lock lock(stop_mutex);
      if (stop_cv.wait_for(lock, std::chrono::seconds(5), [] { return stopping; })) break;
    }
    const uint64_t now = NowNs();
    const uint64_t update_count = updates.count.load(std::memory_order_relaxed);
    const uint64_t swap_count = swaps.count.load(std::memory_order_relaxed);
    const uint64_t ui_count = ui_paints.count.load(std::memory_order_relaxed);
    // Presenter history is synchronized internally. Sampling it here, rather
    // than in OnDraw, keeps observing it when the UI thread stops painting.
    auto* runtime = rex::Runtime::instance();
    auto* graphics = runtime ? runtime->graphics_system() : nullptr;
    auto* presenter = graphics ? graphics->presenter() : nullptr;
    const auto presentations = presenter ? presenter->GetRecentPresentationTimes()
                                         : std::vector<uint64_t>{};
    const uint64_t present_ns = presentations.empty() ? 0 : presentations.back();
    const int64_t update_age = AgeMs(now, updates.last_ns.load(std::memory_order_relaxed));
    const int64_t swap_age = AgeMs(now, swaps.last_ns.load(std::memory_order_relaxed));
    const int64_t ui_age = AgeMs(now, ui_paints.last_ns.load(std::memory_order_relaxed));
    const int64_t present_age = AgeMs(now, present_ns);
    const char* hint = "progressing / not classified";
    if (update_age > 3000 && update_count) {
      hint = "guest update gap (loading/paused or stalled; not proof of ANR)";
    } else if (swap_age > 3000 && swap_count) {
      hint = "guest swap gap (not proof of GPU stall)";
    } else if (ui_age > 3000 && ui_count) {
      hint = "UI paint gap (background or blocked; not proof of ANR)";
    } else if ((present_age > 3000 || (present_age == -1 && now - started > 5'000'000'000)) &&
               update_count > previous_updates) {
      hint = "updates continue, host present gap (investigate renderer/driver)";
    }
    REXLOG_INFO("A730 diagnostic: uptime_ms={} updates+{} total={} age_ms={} "
                "swaps+{} total={} age_ms={} UI_paints+{} age_ms={} "
                "host_present_age_ms={} host_presents_sampled={} hint={}",
                (now - started) / 1'000'000, update_count - previous_updates, update_count,
                update_age, swap_count - previous_swaps, swap_count, swap_age,
                 ui_count - previous_ui, ui_age, present_age, presentations.size(), hint);
    const auto swap_stats = swap_intervals.Snapshot();
    const auto update_stats = update_intervals.Snapshot();
    REXLOG_INFO("frame-window: uptime_ms={} scope=recent_max512_guest_intervals "
                "swap_n={} mean_ms={:.3f} p95_ms={:.3f} p99_ms={:.3f} max_ms={:.3f} "
                "update_n={} mean_ms={:.3f} p95_ms={:.3f} p99_ms={:.3f} max_ms={:.3f} "
                "camera_script_requests={}; not GPU timings or control-ready proof",
                (now - started) / 1000000, swap_stats.samples, swap_stats.mean_us / 1000.0,
                swap_stats.p95_us / 1000.0, swap_stats.p99_us / 1000.0, swap_stats.max_us / 1000.0,
                update_stats.samples, update_stats.mean_us / 1000.0,
                update_stats.p95_us / 1000.0, update_stats.p99_us / 1000.0,
                update_stats.max_us / 1000.0, camera_requests.load());
    const auto swap_delta = swap_intervals.SnapshotSince(swap_cursor);
    const auto update_delta = update_intervals.SnapshotSince(update_cursor);
    const uint32_t hud_updates = g_mcla_vehicle.hud_updates.load(std::memory_order_relaxed);
    const uint32_t hud_delta = hud_updates - previous_hud_updates;
    const float speed = g_mcla_vehicle.speed.load(std::memory_order_relaxed);
    const bool speed_observed = hud_delta != 0 && std::isfinite(speed) && speed >= 0;
    // Read existing atomics only on this worker. Velocity is a window-end HUD
    // observation, not the speed at every sampled frame or a control-ready flag.
    REXLOG_INFO("frame-interval: monotonic_us={} uptime_ms={} window_ms={} "
                "swap_n={} mean_ms={:.3f} p95_ms={:.3f} p99_ms={:.3f} max_ms={:.3f} "
                "swap_dropped={} swap_unavailable={} update_n={} mean_ms={:.3f} "
                "p95_ms={:.3f} p99_ms={:.3f} max_ms={:.3f} "
                "update_dropped={} update_unavailable={} cursor_reset={} "
                "speed_observed={} speed_raw={:.3f} hud_delta={} frozen_hud_frames={} "
                "speed_alignment=window_end_hud_observation_not_per_frame unit=guest_velocity_raw",
                now / 1000, (now - started) / 1000000, (now - previous_report_ns) / 1000000,
                swap_delta.stats.samples, swap_delta.stats.mean_us / 1000.0,
                swap_delta.stats.p95_us / 1000.0, swap_delta.stats.p99_us / 1000.0,
                swap_delta.stats.max_us / 1000.0, swap_delta.dropped, swap_delta.unavailable,
                update_delta.stats.samples, update_delta.stats.mean_us / 1000.0,
                update_delta.stats.p95_us / 1000.0, update_delta.stats.p99_us / 1000.0,
                update_delta.stats.max_us / 1000.0, update_delta.dropped, update_delta.unavailable,
                swap_delta.cursor_reset || update_delta.cursor_reset, speed_observed,
                speed_observed ? double(speed) : -1.0, hud_delta,
                g_mcla_vehicle.frozen_frames.load(std::memory_order_relaxed));
    swap_cursor = swap_delta.next_sequence;
    update_cursor = update_delta.next_sequence;
    previous_hud_updates = hud_updates;
    previous_report_ns = now;
    rex::FlushLogging();
    previous_updates = update_count;
    previous_swaps = swap_count;
    previous_ui = ui_count;
  }
  REXLOG_INFO("A730 diagnostic: watchdog stopped");
  rex::FlushLogging();
}
}  // namespace

void Start() {
  if (worker.joinable()) return;
  {
    std::lock_guard lock(stop_mutex);
    stopping = false;
  }
  worker = std::thread(Run);
}

void Stop() {
  if (!worker.joinable()) return;
  {
    std::lock_guard lock(stop_mutex);
    stopping = true;
  }
  stop_cv.notify_one();
  worker.join();
}

void RecordUpdate() {
  const uint64_t now = NowNs();
  const uint64_t last = updates.last_ns.exchange(now, std::memory_order_relaxed);
  if (last && now >= last) update_intervals.Record((now - last) / 1000);
  updates.count.fetch_add(1, std::memory_order_relaxed);
  // Raw state changes are observed on the guest update thread, not interpreted
  // as spawn/camera/control readiness without a traced semantic contract.
  auto* runtime = rex::Runtime::instance();
  const uint8_t* base = runtime ? runtime->virtual_membase() : nullptr;
  if (base) {
    const uint32_t addresses[] = {0x8288D000, 0x8286CF54, 0x82836A28};
    const char* names[] = {"game_state_raw", "city_pending_raw", "city_lighting_guard_raw"};
    static uint32_t previous[] = {UINT32_MAX, UINT32_MAX, UINT32_MAX};
    for (unsigned i = 0; i < 3; ++i) {
      const uint8_t* p = base + addresses[i];
      const uint32_t value = uint32_t(p[0]) << 24 | uint32_t(p[1]) << 16 |
                             uint32_t(p[2]) << 8 | p[3];
      if (previous[i] != value) { previous[i] = value; Stage(names[i], value); }
    }
  }
}
void RecordSwap() {
  const uint64_t now = NowNs();
  const uint64_t last = swaps.last_ns.exchange(now, std::memory_order_relaxed);
  if (last && now >= last) swap_intervals.Record((now - last) / 1000);
  swaps.count.fetch_add(1, std::memory_order_relaxed);
}
void RecordUiPaint() { ui_paints.Record(NowNs()); }
void RecordStartPress() {
  start_ns.store(NowNs(), std::memory_order_relaxed);
  player_car.store(0, std::memory_order_relaxed);
  Stage("native_pad0_start_edge");
}
void RecordPlayerCar(uint32_t car) {
  if (player_car.exchange(car, std::memory_order_relaxed) != car)
    Stage("player_car_valid_at_hud_not_control_ready", car);
}
void RecordCameraScriptRequest() {
  camera_requests.fetch_add(1, std::memory_order_relaxed);
  const uint64_t now = NowNs();
  uint64_t last = last_camera_log_ns.load(std::memory_order_relaxed);
  if (now - last >= 1000000000 &&
      last_camera_log_ns.compare_exchange_strong(last, now))
    Stage("script_Game_SetCamera_request_not_activation");
}
}  // namespace mcla::diagnostics
#endif
