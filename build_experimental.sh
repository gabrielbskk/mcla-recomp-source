#!/bin/bash
# Experimental build script for mcla-recomp
# Includes: upstream Vulkan paint pipeline fix, all Android patches, all fork features

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "MCLA Recomp Experimental Build"
echo "========================================="
echo "Includes:"
echo "  - All fork features (Android, frame generation, UI timing, reflex sleep assist, etc.)"
echo "  - Upstream Vulkan paint pipeline fix (rexglue-vulkan-paint-pipeline-format.patch)"
echo "  - All 14 Android SDK patches"
echo "========================================="

# Check dependencies
echo "Checking dependencies..."
for cmd in cmake ninja python3 java aapt2 apksigner zipalign readelf clang; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "WARNING: $cmd not found in PATH"
    fi
done

# SDK commit
SDK_COMMIT="f5337cdc947ff6d4c4196737e2c807a48f2a1fc2"
SDK_DIR="$SCRIPT_DIR/third_party/rexglue-sdk"
PATCHES_DIR="$SCRIPT_DIR/patches"

echo ""
echo "Step 1: Setting up ReXGlue SDK..."
if [[ ! -d "$SDK_DIR/.git" ]]; then
    echo "Cloning ReXGlue SDK..."
    git clone --recursive https://github.com/rexglue/rexglue-sdk.git "$SDK_DIR"
fi

cd "$SDK_DIR"
echo "Checking out SDK commit $SDK_COMMIT..."
git fetch origin
git checkout "$SDK_COMMIT"
git submodule update --init --recursive

echo "Applying Android SDK patches..."
PATCHES=(
    "rexglue-android-arm64.patch"
    "rexglue-turnip-loader.patch"
    "rexglue-driver-cache-path.patch"
    "rexglue-vulkan-pipeline-background-work.patch"
    "rexglue-vulkan-occlusion-query-pipeline-wait.patch"
    "rexglue-android-present-filters.patch"
    "rexglue-android-sdl-event-pump.patch"
    "rexglue-pipeline-storage-layout.patch"
    "rexglue-android-present-stats.patch"
    "rexglue-vulkan-frame-start-sync.patch"
    "rexglue-android-bionic-link.patch"
    "rexglue-android-driver-selection.patch"
    "rexglue-android-stereo-diagnostic.patch"
    "rexglue-vulkan-paint-pipeline-format.patch"
)

for patch in "${PATCHES[@]}"; do
    echo "  Applying $patch..."
    git apply "$PATCHES_DIR/$patch" || {
        echo "ERROR: Failed to apply $patch"
        exit 1
    }
done

echo "SDK patches applied successfully."

echo ""
echo "Step 2: Building ReXGlue SDK for Android ARM64..."
SDK_BUILD_DIR="$SDK_DIR/out/build/android-arm64"
SDK_INSTALL_DIR="$SDK_DIR/out/install/android-arm64"

cmake -S "$SDK_DIR" -B "$SDK_BUILD_DIR" -G Ninja \
  -DANDROID=ON -DCMAKE_BUILD_TYPE=Release \
  -DREXGLUE_USE_VULKAN=ON -DREXGLUE_USE_D3D12=OFF \
  -DREXGLUE_ENABLE_FSR=OFF \
  -DCMAKE_INSTALL_PREFIX="$SDK_INSTALL_DIR"

cmake --build "$SDK_BUILD_DIR" -j 4
cmake --install "$SDK_BUILD_DIR"

echo "SDK built and installed."

echo ""
echo "Step 3: Building AdrenoTools..."
cd "$SCRIPT_DIR"
./scripts/build_adrenotools_android.sh

echo ""
echo "Step 4: Running codegen..."
"$SDK_INSTALL_DIR/bin/rexglue" codegen mcla_manifest.toml

echo ""
echo "Step 5: Building game..."
GAME_BUILD_DIR="$SCRIPT_DIR/out/build/mcla-android-arm64"
cmake -S . -B "$GAME_BUILD_DIR" -G Ninja \
  -DANDROID=TRUE -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$SDK_INSTALL_DIR"

cmake --build "$GAME_BUILD_DIR" --target mcla -j 4

echo ""
echo "Step 6: Building APK..."
./scripts/build_android_apk.sh

echo ""
echo "========================================="
echo "Build complete!"
echo "APK should be at: out/android-arm64/MCLA-arm64.apk"
echo "Also copied to: Download/MCLA-Recomp-arm64.apk"
echo "========================================="
