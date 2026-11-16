#!/usr/bin/env python3
"""Extract observations without inferring object classes or camera/control readiness.

Usage: python3 scripts/analyze_android_transition_log.py path/to/mcla.log
Recent-512 windows overlap: never average their percentiles or means together.
"""
import argparse
import json
from pathlib import Path
import re


def analyze(text):
    events, windows, population = [], [], []
    for line in text.splitlines():
        if "transition: " in line:
            events.append(line.split("transition: ", 1)[1])
        if "frame-window: " in line:
            fields = dict(re.findall(r"(\w+)=([^ ;]+)", line.split("frame-window: ", 1)[1]))
            windows.append(fields)
        if "population: " in line:
            population.append(line.split("population: ", 1)[1])
    return {
        "transition_observations": events,
        "recent512_windows_not_disjoint": windows,
        "population_observations": population,
        "pipeline_creation_records_total_including_startup_restore":
            text.count("Creating graphics pipeline state"),
        "acceptance": {
            "all_pedestrians_traffic_parked_zero": "not established by ambient registry alone",
            "opponents_preserved": "requires race/object classification validation",
            "actual_camera_activation": "not instrumented; script request is only a proxy",
            "first_controllable_frame": "not instrumented; HUD-valid car is only a proxy",
            "stutter_fixed": "requires matched before/after traces; no inference from window averages",
        },
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    args = parser.parse_args()
    print(json.dumps(analyze(args.log.read_text(errors="replace")), indent=2))
