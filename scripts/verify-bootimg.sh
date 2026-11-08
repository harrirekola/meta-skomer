#!/bin/sh
# Inspect a skomer-boot.img built by skomer-bootimg.bb.
set -eu

img="${1:-}"
if [ -z "$img" ]; then
    echo "usage: $0 /path/to/skomer-boot.img" >&2
    exit 1
fi

unpack_bootimg --boot_img "$img" | grep -E \
    "kernel_size|kernel load address|ramdisk size|ramdisk load address|second bootloader size|second bootloader load address|tags load address|page size|header version|command line"
