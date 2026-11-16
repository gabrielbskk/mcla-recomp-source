#!/data/data/com.termux/files/usr/bin/sh
# Build reviewed/pinned, locally adapted sources. Never downloads or embeds the user's DLL.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
SOURCE=${MCLA_LSFG_SOURCE_DIR:?Set MCLA_LSFG_SOURCE_DIR to the adapted lsfg-vk v1.0.0 checkout}
OUT="$ROOT/out/build/mcla-lsfg-arm64"
UPSTREAM=${MCLA_LSFG_UPSTREAM_BUILD_DIR:-$OUT/upstream}
[ "$(git -C "$SOURCE" rev-parse HEAD)" = 7113d7d02da9fc9df5cb3b03230d1f7de86f7056 ] || exit 1
[ "$(git -C "$SOURCE/thirdparty/dxbc" rev-parse HEAD)" = 78ab59a8aaeb43cd1b0a5e91ba86722433a10b78 ] || exit 1
[ "$(git -C "$SOURCE/thirdparty/pe-parse" rev-parse HEAD)" = 31ac5966503689d5693cd9fb520bd525a8710e17 ] || exit 1
[ "$(git -C "$SOURCE/thirdparty/toml11" rev-parse HEAD)" = be08ba2be2a964edcdb3d3e3ea8d100abc26f286 ] || exit 1
[ "$(git -C "$SOURCE/thirdparty/volk" rev-parse HEAD)" = be3dbd49bf77052665e96b6c7484af855e7e5f67 ] || exit 1
if ! grep -q 'void Context::record' "$SOURCE/framegen/v3.1p_src/context.cpp"; then
    git -C "$SOURCE" apply --check "$ROOT/patches/lsfg-vk-v1-renderer-adapter.patch"
    git -C "$SOURCE" apply "$ROOT/patches/lsfg-vk-v1-renderer-adapter.patch"
fi
mkdir -p "$OUT"
cmake -S "$SOURCE" -B "$UPSTREAM" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF -DVULKAN_HEADERS_INSTALL_DIR="${PREFIX:?}"
cmake --build "$UPSTREAM" --target lsfg-vk-framegen dxbc pe-parse volk --parallel 2
c++ -std=c++20 -O2 -fPIC -fvisibility=hidden -shared \
    -I "$SOURCE/include" -I "$SOURCE/framegen/include" \
    -I "$SOURCE/framegen/v3.1p_include" -I "$SOURCE/framegen/v3.1p_include/v3_1p" \
    -I "$SOURCE/thirdparty/volk" -I "$SOURCE/thirdparty/pe-parse/pe-parser-library/include" \
    -I "$SOURCE/thirdparty/dxbc/include/dxbc" -I "$SOURCE/thirdparty/dxbc/include/spirv" \
    -I "$SOURCE/thirdparty/dxbc/include/util" -I "$SOURCE/thirdparty/dxbc/include/dxvk" \
    -I "$ROOT/third_party/rexglue-sdk/include" \
    "$ROOT/src/lsfg/mcla_lsfg_backend.cpp" "$SOURCE/src/extract/extract.cpp" "$SOURCE/src/extract/trans.cpp" \
    "$UPSTREAM/framegen/liblsfg-vk-framegen.a" "$UPSTREAM/thirdparty/dxbc/libdxbc.a" \
    "$UPSTREAM/thirdparty/pe-parse/pe-parser-library/libpe-parse.a" "$UPSTREAM/thirdparty/volk/libvolk.a" \
    -ldl -Wl,--exclude-libs,ALL -Wl,--version-script="$ROOT/src/lsfg/mcla_lsfg.exports" \
    -Wl,--threads=1 -Wl,-z,max-page-size=16384 -Wl,-z,defs -o "$OUT/libmcla_lsfg.so"
python3 - "$SOURCE" "$OUT/THIRD-PARTY-NOTICES.txt" <<'PY'
from pathlib import Path
import sys
source, output = map(Path, sys.argv[1:])
parts = ['MCLA optional LSFG bridge. User DLL/shaders are not distributed.\n'
         'lsfg-vk v1.0.0 (7113d7d02da9fc9df5cb3b03230d1f7de86f7056), locally adapted.\n'
         'Source: https://github.com/PancakeTAS/lsfg-vk/tree/v1.0.0\n']
for name, path in [('lsfg-vk', 'LICENSE.md'), ('DXVK DXBC subset', 'thirdparty/dxbc/LICENSE.md'),
                   ('pe-parse', 'thirdparty/pe-parse/LICENSE'), ('toml11', 'thirdparty/toml11/LICENSE'),
                   ('volk', 'thirdparty/volk/LICENSE.md')]:
    parts.append('\n===== ' + name + ' =====\n' + (source / path).read_text())
output.write_text('\n'.join(parts))
PY
echo "Optional backend: $OUT/libmcla_lsfg.so"
