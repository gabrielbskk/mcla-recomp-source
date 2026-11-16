#include "frame_sample_window.h"

#include <atomic>
#include <cassert>
#include <cmath>
#include <iostream>
#include <thread>

int main() {
  using mcla::diagnostics::FrameSampleWindow;
  FrameSampleWindow window;
  auto empty = window.SnapshotSince(0);
  assert(empty.stats.samples == 0 && empty.next_sequence == 0);
  assert(empty.dropped == 0 && empty.unavailable == 0 && !empty.cursor_reset);

  for (unsigned n = 1; n <= 100; ++n) window.Record(n * 1000);
  auto first = window.SnapshotSince(0);
  assert(first.next_sequence == 100 && first.stats.samples == 100);
  assert(first.stats.p95_us == 95000 && first.stats.p99_us == 99000);
  assert(first.dropped == 0 && first.unavailable == 0);
  auto idle = window.SnapshotSince(first.next_sequence);
  assert(idle.stats.samples == 0 && idle.next_sequence == first.next_sequence);

  for (unsigned n = 0; n < 10; ++n) window.Record(20000);
  auto second = window.SnapshotSince(first.next_sequence);
  assert(second.next_sequence == 110 && second.stats.samples == 10);
  assert(second.stats.mean_us == 20000 && second.stats.max_us == 20000);
  assert(second.dropped == 0 && second.unavailable == 0);

  auto reset = window.SnapshotSince(1000);
  assert(reset.cursor_reset && reset.next_sequence == 110 && reset.stats.samples == 0);
  assert(reset.dropped == 0 && reset.unavailable == 0);

  for (unsigned n = 0; n < 1000; ++n) window.Record(33333);
  auto overflow = window.SnapshotSince(110);
  assert(overflow.next_sequence == 1110 && overflow.stats.samples == 512);
  assert(overflow.dropped == 488 && overflow.unavailable == 0);
  assert(overflow.stats.mean_us == 33333 && !overflow.cursor_reset);
  assert(window.Snapshot().samples == overflow.stats.samples);

  FrameSampleWindow saturated;
  saturated.Record(UINT64_MAX);
  assert(saturated.SnapshotSince(0).stats.max_us == UINT32_MAX);

  FrameSampleWindow concurrent;
  std::atomic<bool> done{false};
  std::thread writer([&] {
    for (unsigned i = 0; i < 100000; ++i) concurrent.Record(i % 1000 + 1);
    done.store(true);
  });
  uint64_t cursor = 0;
  do {
    auto delta = concurrent.SnapshotSince(cursor);
    assert(!delta.cursor_reset && delta.next_sequence >= cursor);
    assert(delta.stats.samples + delta.unavailable + delta.dropped == delta.next_sequence - cursor);
    if (delta.stats.samples) {
      assert(delta.stats.samples <= 512 && delta.stats.mean_us >= 1 && delta.stats.max_us <= 1000);
      assert(delta.stats.p95_us <= delta.stats.p99_us);
    }
    cursor = delta.next_sequence;
  } while (!done.load());
  writer.join();
  std::cout << "PASS disjoint frame deltas, overflow accounting, reset and concurrent snapshots\n";
}
