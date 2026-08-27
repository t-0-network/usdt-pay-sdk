#!/bin/sh
set -eu

REPO="t-0-network/usdt-pay-sdk"
BINARY_NAME="usdt-pay"
GITHUB_RELEASE_URL="https://github.com/${REPO}/releases/latest/download"

main() {
    os="$(detect_os)"
    arch="$(detect_arch)"
    asset="${BINARY_NAME}-${os}-${arch}"

    printf "Downloading %s...\n" "${asset}"
    url="${GITHUB_RELEASE_URL}/${asset}"

    tmpdir="$(mktemp -d)"
    trap 'rm -rf "${tmpdir}"' EXIT

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "${tmpdir}/${BINARY_NAME}" "${url}"
    elif command -v wget >/dev/null 2>&1; then
        wget -qO "${tmpdir}/${BINARY_NAME}" "${url}"
    else
        printf "Error: curl or wget is required\n" >&2
        exit 1
    fi

    chmod +x "${tmpdir}/${BINARY_NAME}"

    install_dir="$(pick_install_dir)"
    mkdir -p "${install_dir}"

    mv "${tmpdir}/${BINARY_NAME}" "${install_dir}/${BINARY_NAME}"
    printf "Installed %s to %s/%s\n" "${BINARY_NAME}" "${install_dir}" "${BINARY_NAME}"

    if ! echo "${PATH}" | tr ':' '\n' | grep -qx "${install_dir}"; then
        export PATH="${install_dir}:${PATH}"
    fi

    if [ $# -gt 0 ]; then
        printf "\n"
        "${install_dir}/${BINARY_NAME}" "$@"
    else
        printf "\nUsage:\n  %s init --lang=<language> --role=<role> <project-name>\n" "${BINARY_NAME}"
    fi
}

detect_os() {
    case "$(uname -s)" in
        Linux*)  echo "linux" ;;
        Darwin*) echo "darwin" ;;
        *)
            printf "Error: unsupported OS: %s\n" "$(uname -s)" >&2
            exit 1
            ;;
    esac
}

detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64)   echo "amd64" ;;
        aarch64|arm64)   echo "arm64" ;;
        *)
            printf "Error: unsupported architecture: %s\n" "$(uname -m)" >&2
            exit 1
            ;;
    esac
}

pick_install_dir() {
    if [ -w "/usr/local/bin" ]; then
        echo "/usr/local/bin"
    else
        dir="${HOME}/.local/bin"
        mkdir -p "${dir}"
        echo "${dir}"
    fi
}

main "$@"
