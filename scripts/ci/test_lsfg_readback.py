#!/usr/bin/env python3
"""Validate private known-pattern LSFG readbacks; never opens a game or embeds shaders.

Usage: python3 scripts/ci/test_lsfg_readback.py PRIVATE_OUTPUT_DIR [WIDTH HEIGHT]
Input files are raw RGBA8 from the isolated pixel probe, not screenshots.
This is a narrow device integration test, not a general image-quality metric.
"""
import json
import argparse
from pathlib import Path


def red_stats(pixels, width):
    locations = [i // 4 for i in range(0, len(pixels), 4)
                 if pixels[i] > 150 and pixels[i] > pixels[i + 1] * 1.7
                 and pixels[i] > pixels[i + 2] * 1.7]
    if not locations:
        raise AssertionError("No red test object in readback")
    return (len(locations), sum(i % width for i in locations) / len(locations),
            sum(i // width for i in locations) / len(locations))


def check_motion(first, second, generated, width):
    assert len(first) == len(second) == len(generated) and len(first) % (width * 4) == 0
    assert generated != first and generated != second, "Repeated real frame"
    a, b, out = (red_stats(image, width) for image in (first, second, generated))
    assert abs(a[1] - b[1]) >= 16, "Test motion must be measurable"
    assert min(a[0], b[0]) * .75 <= out[0] <= max(a[0], b[0]) * 1.25, "Object area/ghosting"
    midpoint = ((a[1] + b[1]) / 2, (a[2] + b[2]) / 2)
    assert abs(out[1] - midpoint[0]) <= 4 and abs(out[2] - midpoint[1]) <= 4, "Not near halfway position"
    return {"input_centers": [a[1:], b[1:]], "generated_center": out[1:],
            "expected_midpoint": midpoint, "red_pixels": out[0]}


def expect_rejected(first, second, generated, width, label):
    try:
        check_motion(first, second, generated, width)
    except AssertionError:
        return
    raise AssertionError("Negative control was accepted: " + label)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("width", type=int, nargs="?", default=256)
    parser.add_argument("height", type=int, nargs="?", default=256)
    parser.add_argument("--static-start", type=int, default=1)
    parser.add_argument("--motion-frame", type=int, default=2)
    args = parser.parse_args()
    folder, width, height = args.folder, args.width, args.height
    assert width > 0 and height > 0
    assert 0 <= args.static_start < args.motion_frame
    first, second, generated = [
        (folder / name).read_bytes() for name in
        ("input0.rgba", "input1.rgba", f"generated-{args.motion_frame}.rgba")]
    assert all(len(image) == width * height * 4 for image in (first, second, generated))
    # Validate EVERY static output after the explicit history gate, not a cherry-picked frame.
    errors = []
    for index in range(args.static_start, args.motion_frame):
        static = (folder / f"generated-{index}.rgba").read_bytes()
        assert len(static) == len(first)
        rgb_mae = sum(abs(a - b) for i, (a, b) in enumerate(zip(first, static)) if i % 4 != 3) / (width * height * 3)
        assert rgb_mae <= .25, f"Static warm-history output changed excessively at frame {index}"
        errors.append(rgb_mae)
        reference, observed = red_stats(first, width), red_stats(static, width)
        assert abs(reference[1] - observed[1]) <= 2 and abs(reference[2] - observed[2]) <= 2
        assert reference[0] * .9 <= observed[0] <= reference[0] * 1.1
    result = check_motion(first, second, generated, width)
    expect_rejected(first, second, first, width, "copied first frame")
    expect_rejected(first, second, second, width, "copied second frame")
    expect_rejected(first, second, bytes(len(first)), width, "black output")
    blend = bytes((a + b) // 2 for a, b in zip(first, second))
    expect_rejected(first, second, blend, width, "simple crossfade")
    print(json.dumps({"static_frames": len(errors), "max_static_rgb_mae": max(errors),
                      "discarded_cold_frames": args.static_start, **result}))
    print("PASS warm static image, moving midpoint and negative controls (copies/black/crossfade)")


if __name__ == "__main__":
    main()
