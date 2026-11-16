#!/usr/bin/env python3
"""Compare one observed recent-N window, never aggregate overlapping windows.

Reports extra Start edges as a comparability warning, not a successful A/B.
"""
import argparse
import json
from pathlib import Path
import re


def last_session(text):
    lines = text.splitlines()
    starts = [i for i, line in enumerate(lines) if "PC defaults: clear_memory_page_state" in line]
    return lines[starts[-1]:] if starts else lines


def extract(text, target_ms):
    lines = last_session(text)
    windows = []
    for line in lines:
        if "frame-window: " not in line:
            continue
        prefix, _, rest = line.partition("frame-window: ")
        uptime = int(re.search(r"uptime_ms=(\d+)", rest)[1])
        block = rest.split("swap_n=", 1)[1].split("update_n=", 1)[0]
        values = dict(re.findall(r"(\w+)=([\d.]+)", "n=" + block))
        wall = re.search(r"\[([^\]]+)\]", prefix)
        windows.append({"uptime_ms": uptime, "wall_time": wall[1] if wall else "", "n": int(values["n"]),
                        **{k: float(values[k]) for k in ["mean_ms", "p95_ms", "p99_ms", "max_ms"]}})
    if not windows:
        raise ValueError("No frame-window observations in last session")
    chosen = min(windows, key=lambda w: abs(w["uptime_ms"] - target_ms))
    if abs(chosen["uptime_ms"] - target_ms) > 1500:
        raise ValueError("No window within 1500 ms of target")
    # Limit input observations to the same measured interval, not later gameplay.
    starts = []
    start_us = None
    first_car_ms = None
    for line in lines:
        wall = re.search(r"\[([^\]]+)\]", line)
        if chosen["wall_time"] and wall and wall[1] > chosen["wall_time"]:
            continue
        if "transition: " not in line:
            continue
        stamp = re.search(r"monotonic_us=(\d+)", line)
        if not stamp:
            continue
        now = int(stamp[1])
        if "event=native_pad0_start_edge " in line:
            starts.append(now)
            if start_us is None:
                start_us = now
        if first_car_ms is None and start_us is not None and "event=player_car_valid_at_hud_not_control_ready " in line:
            first_car_ms = (now - start_us) / 1000
    return {"window": chosen, "start_edges_through_window": len(starts),
            "first_start_to_hud_car_ms_not_control_ready": first_car_ms}


def compare(before, after, target_ms):
    a, b = extract(before, target_ms), extract(after, target_ms)
    changes = {k: 100 * (b["window"][k] / a["window"][k] - 1)
               for k in ["mean_ms", "p95_ms", "p99_ms", "max_ms"] if a["window"][k]}
    return {"before": a, "after": b, "observed_percent_change_not_causal_proof": changes,
            "limitations": ["recent windows overlap; do not combine their percentiles",
                            "same N may span different elapsed time and scene states",
                            "extra Start edges, caches, thermal and memory conditions must be reviewed",
                            "HUD car observation is not camera activation or first controllable frame"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--uptime-ms", type=int, default=70000)
    args = parser.parse_args()
    print(json.dumps(compare(args.before.read_text(errors="replace"),
                             args.after.read_text(errors="replace"), args.uptime_ms), indent=2))
