#!/data/data/com.termux/files/usr/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/out/android-arm64"
SDK="$ROOT/out/android-sdk"
PLATFORM="$SDK/android-35/android.jar"
D8="$SDK/build-tools-35.0.1/android-15/lib/d8.jar"
SDL="$ROOT/third_party/rexglue-sdk/thirdparty/sdl3/android-project/app/src/main/java"
LIBS="$ROOT/out/build/mcla-android-arm64"
RUNTIME="$ROOT/third_party/rexglue-sdk/out/install/android-arm64/lib"
ADRENO="$ROOT/out/build/adrenotools-arm64"
LOCAL_SHARE_DIR=${MCLA_LOCAL_SHARE_DIR:-${PREFIX:-/data/data/com.termux/files/usr}/share/mcla-recomp}
DEFAULT_KEYSTORE="$LOCAL_SHARE_DIR/android-debug.keystore"
VERSION_STATE_FILE=${MCLA_VERSION_STATE_FILE:-$LOCAL_SHARE_DIR/version-code}
KEYSTORE_PATH=${MCLA_KEYSTORE_PATH:-$DEFAULT_KEYSTORE}
KEY_ALIAS=${MCLA_KEY_ALIAS:-mcla-debug}
KEYSTORE_PASSWORD=${MCLA_KEYSTORE_PASSWORD:-android}
KEY_PASSWORD=${MCLA_KEY_PASSWORD:-android}

if [ -z "${MCLA_KEYSTORE_PATH+x}" ] && [ ! -f "$KEYSTORE_PATH" ] && [ -f "$OUT/debug.keystore" ]; then
    # Keep the first locally delivered APK upgradeable while users migrate to
    # the stable key outside the repository.
    KEYSTORE_PATH="$OUT/debug.keystore"
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")" "$(dirname "$VERSION_STATE_FILE")"

if [ -n "${MCLA_VERSION_CODE:-}" ]; then
    VERSION_CODE="$MCLA_VERSION_CODE"
    WRITE_VERSION_STATE=false
else
    previous=0
    if [ -f "$VERSION_STATE_FILE" ]; then
        previous=$(tr -d '[:space:]' < "$VERSION_STATE_FILE")
    fi
    case "$previous" in
        ''|*[!0-9]*) echo "Invalid Android version code in $VERSION_STATE_FILE" >&2; exit 1 ;;
    esac
    VERSION_CODE=$((previous + 1))
    WRITE_VERSION_STATE=true
fi
case "$VERSION_CODE" in
    ''|*[!0-9]*|0) echo "MCLA_VERSION_CODE must be a positive integer" >&2; exit 1 ;;
esac
VERSION_NAME=${MCLA_VERSION_NAME:-0.1.$VERSION_CODE}

if [ ! -f "$PLATFORM" ] || [ ! -f "$D8" ]; then
    mkdir -p "$SDK"
    python3 - "$SDK" <<'PY'
import pathlib, sys, urllib.request, zipfile
root = pathlib.Path(sys.argv[1])
downloads = [
    ("platform-35.zip", "https://dl.google.com/android/repository/platform-35_r02.zip",
     ("android-35/android.jar", "android-35/core-for-system-modules.jar")),
    ("build-tools-35.zip", "https://dl.google.com/android/repository/build-tools_r35.0.1_linux.zip",
     ("android-15/lib/d8.jar",)),
]
for archive_name, url, members in downloads:
    archive = root / archive_name
    if not archive.exists():
        print(f"Downloading {url}", flush=True)
        urllib.request.urlretrieve(url, archive)
    with zipfile.ZipFile(archive) as zf:
        for member in members:
            zf.extract(member, root)
PY
fi

for tool in javac java aapt2 apksigner zipalign keytool readelf; do
    command -v "$tool" >/dev/null || { echo "Missing Android packaging tool: $tool" >&2; exit 1; }
done
for lib in libmain.so; do
    [ -f "$LIBS/$lib" ] || { echo "Build $lib first" >&2; exit 1; }
done
MANIFEST="$ROOT/android/AndroidManifest.xml"
if [ "${MCLA_APK_VARIANT:-}" = adreno-diagnostic ]; then
    MANIFEST="$ROOT/android/AndroidManifest-adreno-diagnostic.xml"
    if ! grep -aq 'A730 diagnostic: watchdog started' "$LIBS/libmain.so"; then
        echo 'Diagnostic native library marker missing; rebuild with MCLA_ADRENO_DIAGNOSTIC_APK=ON' >&2
        exit 1
    fi
elif [ -n "${MCLA_APK_VARIANT:-}" ]; then
    echo "Unknown MCLA_APK_VARIANT: $MCLA_APK_VARIANT" >&2
    exit 1
elif grep -aq 'A730 diagnostic: watchdog started' "$LIBS/libmain.so"; then
    echo 'Diagnostic native library detected: pass MCLA_APK_VARIANT=adreno-diagnostic or rebuild with MCLA_ADRENO_DIAGNOSTIC_APK=OFF' >&2
    exit 1
fi
[ -f "$RUNTIME/librexgpu-xenos.so" ] || { echo "Build and install librexgpu-xenos.so first" >&2; exit 1; }
for lib in "$ADRENO/libadrenotools.so" "$ADRENO/src/hook/libhook_impl.so" \
           "$ADRENO/src/hook/libmain_hook.so" \
           "$ADRENO/src/hook/libfile_redirect_hook.so" \
           "$ADRENO/src/hook/libgsl_alloc_hook.so"; do
    [ -f "$lib" ] || { echo "Build AdrenoTools first: missing $lib" >&2; exit 1; }
done

rm -rf "$OUT/stage"
mkdir -p "$OUT/stage/classes" "$OUT/stage/dex" "$OUT/stage/jni/arm64-v8a" "$OUT/stage/apk"

case "${MCLA_S23_PERFORMANCE_DEFAULT:-0}" in
    0) ;;
    1)
        python3 - "$MANIFEST" "$OUT/stage/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ns)
tree = ET.parse(sys.argv[1])
ET.SubElement(tree.getroot().find('application'), 'meta-data', {
    '{'+ns+'}name': 'org.mcla.recomp.S23_PERFORMANCE_DEFAULT',
    '{'+ns+'}value': 'true'})
tree.write(sys.argv[2], encoding='utf-8', xml_declaration=True)
PY
        MANIFEST="$OUT/stage/AndroidManifest.xml"
        ;;
    *) echo 'MCLA_S23_PERFORMANCE_DEFAULT must be 0 or 1' >&2; exit 1 ;;
esac

find "$SDL" -name '*.java' -print > "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaActivity.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaTouchControls.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaTouchEditorActivity.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaLauncherActivity.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaLogExporter.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaLogChunk.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaBuildVariant.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaGpuAdvisor.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaGraphicsProfiles.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaKeyboardMouse.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaAudioTuning.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaPerformanceProfile.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaCheats.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaWorldOptions.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaFrameGeneration.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaTouchLayout.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaTouchStick.java" >> "$OUT/stage/java-sources.txt"
printf '%s\n' "$ROOT/android/MclaAdrenoDiagnostics.java" >> "$OUT/stage/java-sources.txt"
javac -encoding UTF-8 --release 8 -classpath "$PLATFORM" \
    -d "$OUT/stage/classes" @"$OUT/stage/java-sources.txt"
python3 - "$D8" "$PLATFORM" "$OUT/stage/dex" "$OUT/stage/classes" <<'PY'
import pathlib, subprocess, sys
d8, platform, output, classes = sys.argv[1:]
files = sorted(str(path) for path in pathlib.Path(classes).rglob("*.class"))
subprocess.run(["java", "-cp", d8, "com.android.tools.r8.D8", "--release",
                "--min-api", "23", "--lib", platform, "--output", output, *files],
               check=True)
PY

if [ "${MCLA_APK_VARIANT:-}" = adreno-diagnostic ]; then
    aapt2 compile --dir "$ROOT/android/diagnostic-res" -o "$OUT/stage/apk/diagnostic-res.zip"
    aapt2 link -o "$OUT/stage/apk/base.apk" -I "$PLATFORM" \
        --manifest "$MANIFEST" --min-sdk-version 23 --target-sdk-version 35 \
        --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
        -R "$OUT/stage/apk/diagnostic-res.zip"
else
    aapt2 link -o "$OUT/stage/apk/base.apk" -I "$PLATFORM" \
        --manifest "$MANIFEST" --min-sdk-version 23 --target-sdk-version 35 \
        --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"
fi

cp "$LIBS/libmain.so" "$OUT/stage/jni/arm64-v8a/"
if [ "${MCLA_INCLUDE_LSFG:-0}" = 1 ]; then
    [ -f "$ROOT/out/build/mcla-lsfg-arm64/libmcla_lsfg.so" ] || { echo 'Build optional LSFG backend first' >&2; exit 1; }
    cp "$ROOT/out/build/mcla-lsfg-arm64/libmcla_lsfg.so" "$OUT/stage/jni/arm64-v8a/"
    mkdir -p "$OUT/stage/apk/assets"
    cp "$ROOT/out/build/mcla-lsfg-arm64/THIRD-PARTY-NOTICES.txt" "$OUT/stage/apk/assets/lsfg-third-party-notices.txt"
fi
cp "$RUNTIME/librexgpu-xenos.so" "$OUT/stage/jni/arm64-v8a/"
cp "$RUNTIME/libSDL3.so" "$RUNTIME/librexruntime.so" "$OUT/stage/jni/arm64-v8a/"
cp "${PREFIX:?Termux PREFIX is required}/lib/libc++_shared.so" "$OUT/stage/jni/arm64-v8a/"
cp "$ADRENO/libadrenotools.so" "$ADRENO/src/hook/"*.so "$OUT/stage/jni/arm64-v8a/"

python3 - "$OUT/stage/jni/arm64-v8a" <<'PY'
import pathlib, subprocess, sys
for library in sorted(pathlib.Path(sys.argv[1]).glob('*.so')):
    header = subprocess.run(['readelf', '-h', str(library)], check=True,
                            capture_output=True, text=True).stdout
    fields = {line.split(':', 1)[0].strip(): line.split(':', 1)[1].strip()
              for line in header.splitlines() if ':' in line}
    if fields.get('Class') != 'ELF64' or fields.get('Machine') != 'AArch64':
        raise SystemExit(f'{library.name}: expected an ELF64 AArch64 library')
    result = subprocess.run(['readelf', '-Wl', str(library)], check=True,
                            capture_output=True, text=True)
    alignments = [int(line.split()[-1], 0) for line in result.stdout.splitlines()
                  if line.lstrip().startswith('LOAD ')]
    if not alignments or min(alignments) < 16384:
        raise SystemExit(f'{library.name}: ELF LOAD segments are not 16 KiB aligned')
    print(f'{library.name}: ELF64 AArch64, LOAD alignment >= 16 KiB')
PY

python3 - "$OUT/stage/apk/base.apk" "$OUT/stage/dex/classes.dex" "$OUT/stage/jni" <<'PY'
import pathlib, sys, zipfile
apk, dex, jni = map(pathlib.Path, sys.argv[1:])
with zipfile.ZipFile(apk, "a", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
    zf.write(dex, "classes.dex")
    notices = apk.parent / 'assets/lsfg-third-party-notices.txt'
    if notices.is_file():
        zf.write(notices, 'assets/lsfg-third-party-notices.txt')
    for path in sorted(jni.rglob("*.so")):
        zf.write(path, "lib/arm64-v8a/" + path.name,
                 compress_type=zipfile.ZIP_STORED)
PY

if [ ! -f "$KEYSTORE_PATH" ]; then
    keytool -genkeypair -keystore "$KEYSTORE_PATH" \
        -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" \
        -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=MCLA Recomp Debug" >/dev/null 2>&1
fi
zipalign -P 16 -f 4 "$OUT/stage/apk/base.apk" "$OUT/stage/MCLA-aligned.apk"
apksigner sign --ks "$KEYSTORE_PATH" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" \
    --out "$OUT/MCLA-arm64.apk" "$OUT/stage/MCLA-aligned.apk"
apksigner verify "$OUT/MCLA-arm64.apk"
zipalign -c -P 16 4 "$OUT/MCLA-arm64.apk"
if [ "$WRITE_VERSION_STATE" = true ]; then
    printf '%s\n' "$VERSION_CODE" > "$VERSION_STATE_FILE"
fi
echo "Version: $VERSION_NAME (code $VERSION_CODE)"
echo "Keystore: $KEYSTORE_PATH"
echo "APK: $OUT/MCLA-arm64.apk"
DOWNLOADS_DIR=${MCLA_DOWNLOADS_DIR:-/storage/emulated/0/Download}
if [ -d "$DOWNLOADS_DIR" ]; then
    cp "$OUT/MCLA-arm64.apk" "$DOWNLOADS_DIR/MCLA-Recomp-arm64.apk"
    echo "Download APK: $DOWNLOADS_DIR/MCLA-Recomp-arm64.apk"
fi
