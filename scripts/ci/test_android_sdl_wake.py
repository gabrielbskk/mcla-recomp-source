#!/usr/bin/env python3
"""Regression model and source contract for SDL's Android sentinel wake loop.

Not a substitute for device lifecycle/HID integration tests.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
source = ROOT / "third_party/rexglue-sdk/thirdparty/sdl3/src/events/SDL_events.c"
text = source.read_text()
condition = "added > 0 && events[i].type != SDL_EVENT_POLL_SENTINEL"
assert condition in text
assert "if (added_wakeup_event)" in text
assert "Uint64 delay = EVENT_POLL_INTERVAL_NS;" in text
assert "delay = SDL_min(delay, (expiration - now));" in text
# Compile the actual admission condition, not a rewritten Python equivalent.
c = r'''
#include <assert.h>
#include <stdbool.h>
#define SDL_EVENT_POLL_SENTINEL 0x7f00
struct Event { int type; };
static bool wake(const struct Event *events, const int *results, int count) {
 bool added_wakeup_event = false;
 for (int i=0;i<count;i++) {
  int added=results[i];
  if (CONDITION) added_wakeup_event=true;
 }
 return added_wakeup_event;
}
int main(void) {
 struct Event sentinel[]={{SDL_EVENT_POLL_SENTINEL}};
 struct Event real[]={{1}};
 struct Event mixed[]={{SDL_EVENT_POLL_SENTINEL},{1}};
 int accepted[]={1,1}, dropped[]={0,0}, partial[]={1,0};
 assert(!wake(sentinel,accepted,1));
 assert(wake(real,accepted,1));
 assert(wake(mixed,accepted,2));
 assert(!wake(mixed,dropped,2));
 assert(!wake(mixed,partial,2));
 // A pump that adds only bookkeeping must not manufacture another wake.
 int old_immediate_passes=0,new_immediate_passes=0;
 for(int n=0;n<1000;n++) {old_immediate_passes++;if(wake(sentinel,accepted,1))new_immediate_passes++;}
 assert(old_immediate_passes==1000 && new_immediate_passes==0);
}
'''.replace("CONDITION", condition)
with tempfile.TemporaryDirectory(prefix="sdl-wake-") as tmp:
    path = Path(tmp)
    (path / "wake.c").write_text(c)
    subprocess.run(["cc", "-std=c99", "-Wall", "-Wextra", "-Werror", str(path / "wake.c"),
                    "-o", str(path / "test")], check=True)
    subprocess.run([str(path / "test")], check=True)
print("PASS sentinel-only, real/mixed/dropped batches, self-wake model and bounded poll source contracts")
