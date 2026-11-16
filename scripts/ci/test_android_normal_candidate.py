#!/usr/bin/env python3
"""Compile the complete launcher and run pure-Java touch geometry regressions.

Requires a JDK and ANDROID_JAR (defaults to the local packaging platform).
Does not launch the game, modify saves, or validate Android composition/GPU FPS.
"""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAR = Path(os.environ.get("ANDROID_JAR", ROOT / "out/android-sdk/android-35/android.jar"))
SDL = ROOT / "third_party/rexglue-sdk/thirdparty/sdl3/android-project/app/src/main/java"
with tempfile.TemporaryDirectory(prefix="mcla-normal-") as output:
    sources = sorted(SDL.rglob("*.java")) + sorted((ROOT / "android").glob("*.java"))
    sources += [ROOT / "scripts/ci/android-input/TouchLayoutTest.java",
                ROOT / "scripts/ci/android-input/TestLogChunk.java"]
    subprocess.run(["javac", "-encoding", "UTF-8", "--release", "8", "-classpath", str(JAR),
                    "-d", output, *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", output, "org.mcla.recomp.TouchLayoutTest"], check=True)
    subprocess.run(["java", "-cp", output, "org.mcla.recomp.TestLogChunk"], check=True)

activity = (ROOT / "android/MclaActivity.java").read_text()
diagnostics = (ROOT / "android/MclaAdrenoDiagnostics.java").read_text()
profile = (ROOT / "android/MclaPerformanceProfile.java").read_text()
native = (ROOT / "src/perf_options.cpp").read_text()
assert "boolean enabled = false;" in profile, "normal build must not activate S23 preset"
assert "postDelayed(exportLogs" not in activity
assert "MclaLogExporter.exportAsync(app)" not in diagnostics
assert "out.getFD().sync()" not in diagnostics
assert '"REX_MCLA_EMPTY_STREETS"' in (ROOT / "android/MclaWorldOptions.java").read_text()
assert "REXCVAR_DEFINE_BOOL(mcla_empty_streets, false" in native
for switch in ["noautomatictraffic", "notriggeredtraffic", "noparkedcars"]:
    assert '{"' + switch + '",' in native
assert '{"noambientspawn",' not in native, "do not disable all entity spawning"
launcher = (ROOT / "android/MclaLauncherActivity.java").read_text()
exporter = (ROOT / "android/MclaLogExporter.java").read_text()
assert 'export.setOnClickListener' not in launcher
assert 'pickButton.setOnClickListener' in launcher, "game import must remain available"
assert '8 * 1024 * 1024' in exporter
assert 'scheduleWithFixedDelay' in exporter
print("PASS normal profile, automatic worker export, no UI fsync, game import and ambient source contracts")
