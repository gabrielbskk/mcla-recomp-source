#!/usr/bin/env python3
"""Source-contract smoke checks only; complements the independent GPU image tests."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
sdk = root / "third_party/rexglue-sdk"
gpu = (sdk / "src/graphics/vulkan/frame_generation.cpp").read_text()
device = (sdk / "src/ui/vulkan/vulkan_device.cpp").read_text()
presenter = (sdk / "src/ui/presenter.cpp").read_text()
app = (root / "src/mcla_app.cpp").read_text()
assert 'FgGenerateLsfg' in gpu, "LSFG is not wired into the generated-image/compose path"
assert 'mcla_lsfg_record' in gpu, "Missing record-only renderer callback"
assert 'mcla_lsfg_destroy' in gpu, "Missing submission-aware resource retirement"
assert 'REX_MCLA_LSFG' in device, "Missing opt-in capability/feature negotiation"
assert 'synchronization2' in device and 'vulkanMemoryModel' in device
assert '.allowed({"off", "fsr", "lsfg"})' in presenter
assert 'REX_MCLA_LSFG' in app and 'SetFlagFromCommandLine("frame_generation"' in app
print("PASS LSFG SDK source contracts (not gameplay/WSI validation)")
