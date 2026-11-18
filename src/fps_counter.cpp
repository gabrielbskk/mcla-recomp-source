#include "fps_counter.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <mutex>

#ifdef __ANDROID__
#include <jni.h>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/runtime.h>
#include <rex/system/interfaces/graphics.h>
#include <rex/ui/presenter.h>

REXCVAR_DECLARE(bool, mcla_fps_overlay);
REXCVAR_DECLARE(bool, mcla_fps_log);
#endif

namespace mcla {
namespace {
constexpr size_t kCapacity = 256;
constexpr uint64_t kWindowNs = 2'000'000'000;
std::mutex mutex;
std::array<uint64_t, kCapacity> updates{};
size_t total_updates = 0;
uint64_t last_update_ns = 0;

uint64_t NowNs() {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

GameUpdateStats ComputeStatsLocked(uint64_t now) {
  GameUpdateStats result{};
  const size_t count = std::min(total_updates, updates.size());
  std::array<double, kCapacity> intervals{};
  size_t n = 0;
  uint64_t previous = 0;
  const uint64_t cutoff = now > kWindowNs ? now - kWindowNs : 0;
  for (size_t i = 0; i < count; ++i) {
    const uint64_t timestamp = updates[(total_updates - count + i) % updates.size()];
    if (timestamp < cutoff) continue;
    if (previous && timestamp > previous) {
      intervals[n++] = double(timestamp - previous) / 1e6;
    }
    previous = timestamp;
  }
  if (n == 0 || now - previous > 1'000'000'000) return result;
  double sum_ms = 0;
  for (size_t i = 0; i < n; ++i) sum_ms += intervals[i];
  result.fps = double(n) * 1000.0 / sum_ms;
  const size_t p95_idx = (n * 95 + 99) / 100 - 1;
  std::nth_element(intervals.begin(), intervals.begin() + p95_idx, intervals.begin() + n);
  result.p95_ms = intervals[p95_idx];
  result.samples = n;
  return result;
}

#ifdef __ANDROID__
uint64_t last_sampled_ns = 0;
uint64_t last_logged_ns = 0;
char hud_text[96] = "Game updates --";

void SampleAndroidPerf(uint64_t now_ns, const GameUpdateStats& game) {
  double pres_fps = 0, pres_p50 = 0, pres_p95 = 0;
  size_t pres_samples = 0;
  auto* runtime = rex::Runtime::instance();
  auto* graphics = runtime ? runtime->graphics_system() : nullptr;
  auto* presenter = graphics ? graphics->presenter() : nullptr;
  if (presenter) {
    auto times = presenter->GetRecentPresentationTimes();
    const uint64_t cutoff = now_ns > kWindowNs ? now_ns - kWindowNs : 0;
    times.erase(times.begin(), std::lower_bound(times.begin(), times.end(), cutoff));
    pres_samples = times.size();
    if (times.size() >= 2 && times.back() > times.front()) {
      pres_fps = double(times.size() - 1) * 1e9 / double(times.back() - times.front());
      std::array<double, kCapacity> intervals{};
      const size_t count = std::min(times.size() - 1, intervals.size());
      for (size_t i = 0; i < count; ++i) {
        intervals[i] = double(times[i + 1] - times[i]) / 1e6;
      }
      const size_t mid = (count - 1) / 2;
      std::nth_element(intervals.begin(), intervals.begin() + mid, intervals.begin() + count);
      pres_p50 = intervals[mid];
      const size_t p95_idx = (count * 95 + 99) / 100 - 1;
      std::nth_element(intervals.begin(), intervals.begin() + p95_idx, intervals.begin() + count);
      pres_p95 = intervals[p95_idx];
    }
  }
  {
    std::lock_guard lock(mutex);
    if (game.samples) {
      std::snprintf(hud_text, sizeof(hud_text), "%.1f FPS  p95 %.0f ms", game.fps, game.p95_ms);
    } else {
      std::snprintf(hud_text, sizeof(hud_text), "Game updates --");
    }
  }
  if (REXCVAR_GET(mcla_fps_log) && now_ns - last_logged_ns >= 5'000'000'000ULL) {
    last_logged_ns = now_ns;
    REXLOG_INFO(
        "host presentation (recent 2s): {:.2f} FPS, p50 {:.2f} ms, p95 {:.2f} ms ({} samples); "
        "game updates: {:.2f} FPS, p95 {:.2f} ms ({} samples)",
        pres_fps, pres_p50, pres_p95, pres_samples, game.fps, game.p95_ms, game.samples);
  }
}
#endif
}  // namespace

void RecordGameUpdate() {
  const uint64_t now = NowNs();
  bool should_sample = false;
  GameUpdateStats stats{};
  {
    std::lock_guard lock(mutex);
    updates[total_updates++ % updates.size()] = now;
    last_update_ns = now;
#ifdef __ANDROID__
    if (now - last_sampled_ns >= 500'000'000ULL &&
        (REXCVAR_GET(mcla_fps_overlay) || REXCVAR_GET(mcla_fps_log))) {
      last_sampled_ns = now;
      stats = ComputeStatsLocked(now);
      should_sample = true;
    }
#endif
  }
  MclaTickVideoBorders();
#ifdef __ANDROID__
  if (should_sample) {
    SampleAndroidPerf(now, stats);
  }
#endif
}

GameUpdateStats GetGameUpdateStats() {
  const uint64_t now = NowNs();
  std::lock_guard lock(mutex);
  return ComputeStatsLocked(now);
}
}  // namespace mcla

#ifdef __ANDROID__
extern "C" JNIEXPORT jstring JNICALL
Java_org_mcla_recomp_MclaActivity_nativePollPerfOverlay(JNIEnv* env, jclass) {
  if (!REXCVAR_GET(mcla_fps_overlay)) {
    return env->NewStringUTF("");
  }
  const uint64_t now = mcla::NowNs();
  char copy[96];
  {
    std::lock_guard lock(mcla::mutex);
    if (mcla::last_update_ns == 0 || now - mcla::last_update_ns > 1'000'000'000ULL) {
      std::snprintf(copy, sizeof(copy), "Game updates --");
    } else {
      std::snprintf(copy, sizeof(copy), "%s", mcla::hud_text);
    }
  }
  return env->NewStringUTF(copy);
}
#endif
