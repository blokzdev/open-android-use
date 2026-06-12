#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
module_dir="${repo_root}/apps/OpenAndroidUseCompanion"
out_dir="${repo_root}/dist/companion"

print_help() {
  cat <<'EOF'
Usage:
  scripts/build-open-android-use-companion.sh [options]

Builds the on-device companion APK (debug-signed). Requires the Android SDK
(ANDROID_HOME or ANDROID_SDK_ROOT) and Gradle 8.7+ on PATH.

Options:
  --out-dir <dir>  Output directory. Defaults to dist/companion.
  -h, --help       Show help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out-dir)
      out_dir="$(cd "${repo_root}" && mkdir -p "$2" && cd "$2" && pwd)"
      shift 2
      ;;
    -h|--help)
      print_help
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      print_help >&2
      exit 1
      ;;
  esac
done

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must point at an Android SDK." >&2
  exit 1
fi

(
  cd "${module_dir}"
  gradle assembleDebug --no-daemon
)

mkdir -p "${out_dir}"
cp "${module_dir}/app/build/outputs/apk/debug/app-debug.apk" \
   "${out_dir}/open-android-use-companion.apk"

echo "Built companion APK: ${out_dir}/open-android-use-companion.apk"
