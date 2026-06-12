#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_os="$(go env GOOS 2>/dev/null || echo linux)"
arch="$(go env GOARCH 2>/dev/null || echo arm64)"
configuration="release"
out_dir="${repo_root}/dist/android-bridge"

print_help() {
  cat <<'EOF'
Usage:
  scripts/build-open-android-use.sh [options]

The Android runtime is a host-side bridge: it runs on the developer's machine
and drives a device or emulator over adb, so the build target is the host OS.

Options:
  --os darwin|linux|windows  Host target OS. Defaults to the current host.
  --arch arm64|amd64         Host target architecture. Defaults to the current host.
  --configuration release    Reserved for parity with the macOS build script.
  --out-dir <dir>            Output directory. Defaults to dist/android-bridge.
  -h, --help                 Show help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --os)
      target_os="${2:?--os requires a value}"
      shift 2
      ;;
    --arch)
      arch="${2:?--arch requires a value}"
      shift 2
      ;;
    --configuration)
      configuration="${2:?--configuration requires a value}"
      shift 2
      ;;
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

case "${target_os}" in
  darwin|linux|windows) ;;
  *)
    echo "Unsupported host OS: ${target_os}" >&2
    exit 1
    ;;
esac

case "${arch}" in
  arm64|amd64) ;;
  *)
    echo "Unsupported host arch: ${arch}" >&2
    exit 1
    ;;
esac

version="$(node -e "console.log(require('./package.json').version)")"
module_dir="${repo_root}/apps/OpenAndroidUse"
output_dir="${out_dir}/${target_os}/${arch}"
binary_name="open-android-use"
if [[ "${target_os}" == "windows" ]]; then
  binary_name="open-android-use.exe"
fi
mkdir -p "${output_dir}"

(
  cd "${module_dir}"
  GOOS="${target_os}" GOARCH="${arch}" CGO_ENABLED=0 go build \
    -trimpath \
    -ldflags "-s -w -X main.version=${version}" \
    -o "${output_dir}/${binary_name}" \
    .
)

echo "Built ${configuration} Android bridge runtime: ${output_dir}/${binary_name}"
