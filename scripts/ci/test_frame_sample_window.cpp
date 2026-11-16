#include "frame_sample_window.h"

#include <cassert>
#include <atomic>
#include <cmath>
#include <iostream>
#include <thread>

int main() {
  using mcla::diagnostics::FrameSampleWindow;
  FrameSampleWindow window;
  assert(window.Snapshot().samples == 0);
  for (unsigned n = 1; n <= 100; ++n) window.Record(n * 1000);
  auto stats = window.Snapshot();
  assert(stats.samples == 100 && std::abs(stats.mean_us - 50500) < .01);
  assert(stats.p95_us == 95000 && stats.p99_us == 99000 && stats.max_us == 100000);
  for (unsigned n = 0; n < 2000; ++n) window.Record(33333);
  stats = window.Snapshot();
  assert(stats.samples == 512 && stats.p99_us == 33333 && stats.mean_us == 33333);
  FrameSampleWindow concurrent;
  std::atomic<bool> done{false};
  std::thread writer([&] {
    for (unsigned i = 0; i < 100000; ++i) concurrent.Record(i % 1000 + 1);
    done.store(true);
  });
  do {
    auto snapshot = concurrent.Snapshot();
    if (!snapshot.samples) continue;
    assert(snapshot.samples <= 512 && snapshot.mean_us >= 1 && snapshot.max_us <= 1000);
    assert(snapshot.p95_us <= snapshot.p99_us && snapshot.p99_us <= snapshot.max_us);
  } while (!done.load());
  writer.join();
  std::cout << "PASS frame sample statistics, bounded overwrite and concurrent snapshots\n";
}
