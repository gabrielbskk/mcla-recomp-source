#!/usr/bin/env python3
"""Offline checks. Does not establish GPU, NPC runtime or camera acceptance."""
import importlib.util
from pathlib import Path
import re
import subprocess
import tempfile
import tomllib

ROOT = Path(__file__).resolve().parents[2]
subprocess.run(["python3", str(ROOT / "scripts/ci/test_android_normal_candidate.py")], check=True)
with tempfile.TemporaryDirectory(prefix="mcla-v58-") as output:
    subprocess.run(["javac", "--release", "8", "-d", output,
                    str(ROOT / "android/MclaTouchStick.java"),
                    str(ROOT / "scripts/ci/android-input/TouchStickTest.java")], check=True)
    subprocess.run(["java", "-cp", output, "org.mcla.recomp.TouchStickTest"], check=True)
    executable = str(Path(output) / "frame-stats")
    subprocess.run(["c++", "-std=c++17", "-Wall", "-Wextra", "-Werror", "-pthread",
                    "-I", str(ROOT / "src"), str(ROOT / "scripts/ci/test_frame_sample_window.cpp"),
                    "-o", executable], check=True)
    subprocess.run([executable], check=True)

# Audit application-owned Java literals (comments excluded by extracting strings).
# User-provided file names, game text, third-party driver metadata are not UI translations.
for path in (ROOT / "android").glob("*.java"):
    for literal in re.findall(r'"(?:[^"\\]|\\.)*"', path.read_text()):
        assert not re.search(r"[À-ž]", literal), f"Non-English Latin literal: {path}: {literal}"
        assert not re.search(r"\b(Jogar|Arquivos|Controles|Cidade|pedestres|reiniciar|Remover)\b", literal), literal

touch = (ROOT / "android/MclaTouchControls.java").read_text()
assert "animateFeedback" not in touch and "stickOrigins" not in touch
assert "leftPressure = lt;" in touch and "rightPressure = rt;" in touch
assert "control == LT ? leftPressure : rightPressure" in touch
assert "leftStick.x, leftStick.y, rightStick.x, rightStick.y" in touch
assert "stick.x / 32767f" in touch and "stick.y / 32767f" in touch
hooks = tomllib.loads((ROOT / "config/ambient_population.toml").read_text())["midasm_hook"]
assert {h["address"] for h in hooks} == {0x826E1418,0x826D5D90,0x826D5E20,0x826D5EB0,0x822C9E18}
admission = next(h for h in hooks if h["address"] == 0x826E1418)
assert admission["return_on_true"] and admission["registers"] == ["r3"]
native = (ROOT / "src/perf_options.cpp").read_text()
assert "r3.s64 = -1;" in native and "registry_active=unknown" in native
assert "base[0x8282A7C8] = base[0x8282A7C9] = base[0x8282A7CA] = 0;" in native
spec = importlib.util.spec_from_file_location("transition", ROOT / "scripts/analyze_android_transition_log.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
report = module.analyze("frame-window: swap_n=10 mean_ms=33.3 p95_ms=40\n"
                        "population: registry_active=unknown\ntransition: event=test\n")
assert len(report["recent512_windows_not_disjoint"]) == 1
assert "not established" in report["acceptance"]["all_pedestrians_traffic_parked_zero"]
print("PASS English literal inventory, pressure/stick binding and native hook/analyzer contracts")
