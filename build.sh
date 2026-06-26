#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${ROOT}/build"
INSTALL_PREFIX="${INSTALL_PREFIX:-${HOME}/.local}"

clean() {
    rm -rf "${BUILD_DIR}"
    echo "Cleaned ${BUILD_DIR}"
}

build() {
    cmake -B "${BUILD_DIR}" -DCMAKE_BUILD_TYPE=Release
    cmake --build "${BUILD_DIR}"
    echo "Built ${BUILD_DIR}/stackSquing"
}

install() {
    cmake --install "${BUILD_DIR}" --prefix "${INSTALL_PREFIX}"
    echo "Installed to ${INSTALL_PREFIX}/bin/stackSquing"
}

usage() {
    cat <<EOF
Usage: $(basename "$0") [clean|build|install|all]

  clean    Remove the build directory
  build    Configure and compile the project
  install  Install stackSquing to PREFIX/bin (default: ~/.local)
  all      clean, then build, then install (default)

Environment:
  INSTALL_PREFIX  Install destination (default: ~/.local)
EOF
}

main() {
    local action="${1:-all}"

    case "${action}" in
        clean) clean ;;
        build) build ;;
        install) install ;;
        all)
            clean
            build
            install
            ;;
        -h|--help|help) usage ;;
        *)
            echo "Unknown action: ${action}" >&2
            usage >&2
            exit 1
            ;;
    esac
}

main "$@"
