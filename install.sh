#!/usr/bin/env bash
# Install stackSquing from GitHub (macOS / Linux / Windows WSL or Git Bash).
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/Flapjackpants/stackSquing/main/install.sh | bash
set -euo pipefail

REPO="Flapjackpants/stackSquing"
INSTALL_PREFIX="${INSTALL_PREFIX:-${HOME}/.local}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/stackSquing.XXXXXX")"

cleanup() {
    rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

info() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

need_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing dependency: $1"
}

detect_os() {
    case "$(uname -s)" in
        Darwin) echo "macos" ;;
        Linux) echo "linux" ;;
        MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
        *) die "unsupported OS: $(uname -s). Use macOS, Linux, or Windows WSL/Git Bash." ;;
    esac
}

check_deps() {
    local os="$1"
    need_cmd curl
    need_cmd tar
    need_cmd cmake

    if ! command -v c++ >/dev/null 2>&1 && ! command -v g++ >/dev/null 2>&1 && ! command -v clang++ >/dev/null 2>&1; then
        die "missing C++17 compiler (install clang/gcc, or Xcode CLT on macOS)"
    fi

    case "${os}" in
        linux)
            info "Linux: ensure ncurses headers are installed (e.g. sudo apt install build-essential cmake libncurses-dev)"
            ;;
        macos)
            if ! xcode-select -p >/dev/null 2>&1; then
                die "Xcode Command Line Tools required (run: xcode-select --install)"
            fi
            ;;
        windows)
            info "Windows detected — building via Git Bash/MSYS. Prefer WSL if cmake/ncurses are missing."
            ;;
    esac
}

download_source() {
    local ref="${STACKSQUING_REF:-main}"
    local archive="${TMP_DIR}/src.tar.gz"
    local urls=(
        "https://codeload.github.com/${REPO}/tar.gz/refs/tags/${ref}"
        "https://codeload.github.com/${REPO}/tar.gz/refs/heads/${ref}"
    )

    info "Downloading ${REPO}@${ref}"
    local ok=0
    for url in "${urls[@]}"; do
        if curl -fsL "${url}" -o "${archive}" 2>/dev/null; then
            ok=1
            break
        fi
    done
    [[ "${ok}" -eq 1 ]] || die "download failed for ref '${ref}'. Check the name or your network."

    tar -xzf "${archive}" -C "${TMP_DIR}"
    local src
    src="$(find "${TMP_DIR}" -mindepth 1 -maxdepth 1 -type d -name 'stackSquing-*' | head -n 1)"
    [[ -n "${src}" ]] || die "could not unpack source archive"
    echo "${src}"
}

build_and_install() {
    local src="$1"
    info "Building (Release)"
    cmake -S "${src}" -B "${src}/build" -DCMAKE_BUILD_TYPE=Release >&2 || die "cmake configure failed"
    cmake --build "${src}/build" --config Release >&2 || die "build failed"

    info "Installing to ${INSTALL_PREFIX}/bin/stackSquing"
    mkdir -p "${INSTALL_PREFIX}/bin"
    cmake --install "${src}/build" --prefix "${INSTALL_PREFIX}" >&2 || die "install failed"

    local bin="${INSTALL_PREFIX}/bin/stackSquing"
    [[ -x "${bin}" ]] || die "install failed: ${bin} not found"
    echo "${bin}"
}

ensure_path_hint() {
    local prefix_bin="${INSTALL_PREFIX}/bin"
    case ":${PATH}:" in
        *":${prefix_bin}:"*) ;;
        *)
            printf '\nAdd this to your shell profile so stackSquing is on PATH:\n'
            printf '  export PATH="%s:$PATH"\n' "${prefix_bin}"
            ;;
    esac
}

main() {
    local os
    os="$(detect_os)"
    info "Installing stackSquing for ${os}"
    check_deps "${os}"

    local src bin
    src="$(download_source)" || exit 1
    bin="$(build_and_install "${src}")" || exit 1

    printf '\nInstalled: %s\n' "${bin}"
    ensure_path_hint
    printf '\nTry:\n  stackSquing /path/to/material_list.txt\n'
}

main "$@"
