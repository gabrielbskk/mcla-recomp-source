#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>

namespace mcla::diagnostics {
// Bounded observation only. Producer performs two atomics, no allocation/I/O.
// Each slot atomically publishes its generation and microseconds together;
// snapshots ignore unpublished/overwritten slots instead of reading torn data.
class FrameSampleWindow {
 public:
  static constexpr uint64_t kCapacity = 512;
  struct Stats {
    uint32_t samples = 0, p95_us = 0, p99_us = 0, max_us = 0;
    double mean_us = 0;
  };
  struct DeltaSnapshot {
    Stats stats;
    uint64_t next_sequence = 0, dropped = 0, unavailable = 0;
    bool cursor_reset = false;
  };
  FrameSampleWindow() {
    for (auto& slot : slots_) slot.store(0, std::memory_order_relaxed);
  }
  void Record(uint64_t microseconds) {
    const uint64_t n = next_.fetch_add(1, std::memory_order_relaxed);
    const uint64_t value = (uint64_t(uint32_t(n + 1)) << 32) |
                          std::min(microseconds, uint64_t(UINT32_MAX));
    slots_[n % kCapacity].store(value, std::memory_order_release);
  }
  Stats Snapshot() const {
    return SnapshotSince(0).stats;
  }
  DeltaSnapshot SnapshotSince(uint64_t cursor) const {
    DeltaSnapshot result;
    const uint64_t end = next_.load(std::memory_order_acquire);
    result.next_sequence = end;
    if (cursor > end) {
      result.cursor_reset = true;
      return result;
    }
    const uint64_t begin = std::max(cursor, end > kCapacity ? end - kCapacity : 0);
    result.dropped = begin - cursor;
    std::array<uint32_t, kCapacity> samples;
    std::size_t count = 0;
    uint64_t sum = 0;
    for (uint64_t n = begin; n < end; ++n) {
      const uint64_t value = slots_[n % kCapacity].load(std::memory_order_acquire);
      if (uint32_t(value >> 32) != uint32_t(n + 1)) {
        ++result.unavailable;
        continue;
      }
      samples[count++] = uint32_t(value);
      sum += uint32_t(value);
    }
    if (count == 0) return result;
    Stats& stats = result.stats;
    std::sort(samples.begin(), samples.begin() + count);
    stats.samples = uint32_t(count);
    stats.mean_us = double(sum) / count;
    stats.p95_us = samples[(count * 95 + 99) / 100 - 1];
    stats.p99_us = samples[(count * 99 + 99) / 100 - 1];
    stats.max_us = samples[count - 1];
    return result;
  }
 private:
  std::atomic<uint64_t> next_{0};
  std::array<std::atomic<uint64_t>, kCapacity> slots_{};
};
}  // namespace mcla::diagnostics
