#!/usr/bin/env python3
"""Create a v0 Android boot image for the Samsung Skomer (U8500).

U-Boot 2023.04 on this device accepts a v0 image and uses the `second`
area as the kernel FDT. This is the same layout the working pmOS image
uses.

Usage:
  skomer-mkbootimg.py --kernel zImage --dtb skomer.dtb \
      --cmdline "console=ttyAMA2,115200 root=/dev/mmcblk0p2 rw rootwait" \
      --output skomer-boot.img

If --ramdisk is omitted the tool embeds a minimal, valid cpio.gz archive
with no /init. This is required by the stock U-Boot: its fastboot "boot"
command calls bootm, and bootm's v0 Android-image code rejects an image
whose ramdisk_size is 0 and then resets the board. Linux finds no /init
in the placeholder, falls back to prepare_namespace(), and mounts the
root= device normally.
"""

import argparse
import gzip
import hashlib
import os
import struct

DEFAULT_PAGE = 2048
DEFAULT_KERNEL_OFFSET = 0x00008000
DEFAULT_RAMDISK_OFFSET = 0x01000000
DEFAULT_SECOND_OFFSET = 0x00F00000
DEFAULT_TAGS_OFFSET = 0x00000100


def align(value, page):
    return (value + page - 1) & ~(page - 1)


def read_file(path):
    if not path:
        return b""
    with open(path, "rb") as handle:
        return handle.read()


def minimal_ramdisk():
    """Return a valid gzip-compressed newc cpio archive with no /init.

    The stock U-Boot 2023.04 handles ``fastboot boot`` by calling
    ``bootm`` on the downloaded image. In its v0 Android-image support,
    ``android_image_get_ramdisk()`` returns -1 when ``ramdisk_size`` is 0,
    which makes ``bootm`` fail; ``fastboot_boot()`` then calls
    ``do_reset()``, so the phone reboots instead of booting. pmOS images
    never hit this because they always carry an initramfs.

    U-Boot only needs ``ramdisk_size`` to be non-zero. Linux then fails to
    find ``/init`` in this archive, sets ``ramdisk_execute_command`` to
    NULL and falls back to ``prepare_namespace()``, which mounts the
    ``root=`` device from the kernel command line. So a ramdisk that
    contains only the cpio trailer is enough to keep both happy.
    """
    name = b"TRAILER!!!"
    fields = [0] * 13
    fields[11] = len(name) + 1  # namesize includes the trailing NUL
    header = b"070701" + b"".join(b"%08X" % value for value in fields)
    data = header + name + b"\0"
    data += b"\0" * ((4 - len(data) % 4) % 4)
    return gzip.compress(data, 9, mtime=0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kernel", required=True, help="zImage")
    parser.add_argument("--ramdisk", help="optional initramfs (cpio.gz)")
    parser.add_argument("--dtb", required=True, help="device tree blob")
    parser.add_argument("--cmdline", default="", help="kernel command line")
    parser.add_argument("--output", required=True, help="output boot image")
    parser.add_argument("--page-size", type=lambda v: int(v, 0), default=DEFAULT_PAGE)
    parser.add_argument("--base", type=lambda v: int(v, 0), default=0)
    parser.add_argument("--kernel-offset", type=lambda v: int(v, 0),
                        default=DEFAULT_KERNEL_OFFSET)
    parser.add_argument("--ramdisk-offset", type=lambda v: int(v, 0),
                        default=DEFAULT_RAMDISK_OFFSET)
    parser.add_argument("--second-offset", type=lambda v: int(v, 0),
                        default=DEFAULT_SECOND_OFFSET)
    parser.add_argument("--tags-offset", type=lambda v: int(v, 0),
                        default=DEFAULT_TAGS_OFFSET)
    args = parser.parse_args()

    if args.page_size not in (2048, 4096, 8192, 16384):
        raise SystemExit("unsupported page size")

    kernel = read_file(args.kernel)
    if args.ramdisk:
        ramdisk = read_file(args.ramdisk)
    else:
        # U-Boot's fastboot boot path rejects v0 images with ramdisk_size 0
        # (it resets the board). See minimal_ramdisk().
        ramdisk = minimal_ramdisk()
    second = read_file(args.dtb)
    if not kernel:
        raise SystemExit("empty kernel")
    if not second:
        raise SystemExit("empty DTB")

    cmdline = args.cmdline.encode("utf-8")
    if len(cmdline) > 511:
        raise SystemExit("cmdline must be <= 511 bytes")

    kernel_addr = args.base + args.kernel_offset
    ramdisk_addr = args.base + args.ramdisk_offset
    second_addr = args.base + args.second_offset
    tags_addr = args.base + args.tags_offset

    # U-Boot does not verify the ID; keep it deterministic anyway.
    image_id = hashlib.sha1(kernel + ramdisk + second).digest()[:8]

    header = struct.pack(
        "<8s10I16s512s8s1024s",
        b"ANDROID!",
        len(kernel), kernel_addr,
        len(ramdisk), ramdisk_addr,
        len(second), second_addr,
        tags_addr, args.page_size, 0, 0,
        b"", cmdline, image_id, b"",
    )
    if len(header) != 1608:
        raise SystemExit("internal error: unexpected v0 header size")

    out = bytearray(header.ljust(args.page_size, b"\0"))
    out += kernel.ljust(align(len(kernel), args.page_size), b"\0")
    out += ramdisk.ljust(align(len(ramdisk), args.page_size), b"\0")
    out += second.ljust(align(len(second), args.page_size), b"\0")

    out_dir = os.path.dirname(os.path.abspath(args.output))
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    with open(args.output, "wb") as handle:
        handle.write(out)

    print("wrote %s: %d bytes (kernel %d, ramdisk %d, second %d)" %
          (args.output, len(out), len(kernel), len(ramdisk), len(second)))


if __name__ == "__main__":
    main()
