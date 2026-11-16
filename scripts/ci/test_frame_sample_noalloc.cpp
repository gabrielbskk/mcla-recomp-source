#include "frame_sample_window.h"

#include <atomic>
#include <cstdlib>
#include <iostream>
#include <new>

namespace {
std::atomic<unsigned> allocations{0};
}

void* operator new(std::size_t size) {
  if (void* pointer = std::malloc(size ? size : 1)) {
    allocations.fetch_add(1, std::memory_order_relaxed);
    return pointer;
  }
  throw std::bad_alloc();
}
void operator delete(void* pointer) noexcept { std::free(pointer); }
void operator delete(void* pointer, std::size_t) noexcept { std::free(pointer); }

int main() {
  mcla::diagnostics::FrameSampleWindow window;
  for (unsigned i = 0; i < 700; ++i) window.Record(33000 + i);
  const unsigned before = allocations.load();
  const auto recent = window.Snapshot();
  const auto delta = window.SnapshotSince(650);
  const auto empty = window.SnapshotSince(700);
  const unsigned used = allocations.load() - before;
  if (used != 0) {
    std::cerr << "FAIL: frame statistics allocated " << used << " heap objects\n";
    return 1;
  }
  if (recent.samples != 512 || delta.stats.samples != 50 || empty.stats.samples != 0) return 2;
  std::cout << "PASS no heap allocations in recent/disjoint/empty frame snapshots\n";
}
