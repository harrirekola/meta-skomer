# meta-skomer

Yocto layer for the **Samsung Galaxy Xcover 2 (GT-S7710, codename `skomer`)**,
an ST-Ericsson NovaThor U8500 device with a dual-core ARM Cortex-A9, 1 GiB of
RAM, a 4 GB eMMC, and a microSD slot.

This is a **bring-up layer**, not a complete phone OS. It builds a minimal
systemd image that boots from a microSD card under the phone's stock U-Boot and
is reachable over USB networking (`ssh root@172.16.42.1`) without a UART cable.

## Important: the eMMC is read-only

The eMMC user area on this device is permanently write-protected. The stock
Samsung U-Boot is still readable in the eMMC `Kernel` partition, but it cannot
accept new data. This layer therefore:

- **never writes the eMMC**;
- keeps the kernel and root filesystem on the microSD card;
- expects the stock U-Boot to load the kernel from a host (`fastboot boot`) or
  from the SD card (UART / manual U-Boot commands).

Do not try to flash the generated images to the eMMC.

## How it boots

```text
             eMMC (read-only)                         microSD card
        +----------------------+              +---------------------------+
PC ---> | stock U-Boot 2023.04 |   RAM boot   | p1 FAT: zImage, DTB,      |
USB     | (Kernel partition)   | -----------> |        boot.scr           |
        +----------------------+              | p2 ext4: rootfs           |
                                              +---------------------------+
                                                        ^
                                                        |
                                          root=/dev/mmcblk0p2
```

The stock U-Boot has a non-persistent environment and does **not** scan the SD
card automatically, so every power-on needs a trigger:

- **Primary:** `fastboot boot skomer-boot.img` from a PC or a small
  USB-OTG host (e.g. a Pi Zero).
- **Alternative:** UART at the U-Boot prompt, then `fatload`/`source boot.scr`.

The SD card is `/dev/mmcblk0` in Linux and `mmc 1` in U-Boot.

## Layer contents

| Path | Purpose |
|---|---|
| `conf/machine/skomer.conf` | Machine definition: tune, kernel, DTB, image formats, boot addresses |
| `conf/layer.conf` | Layer registration (`openembedded-layer` is required for `mmc-utils`) |
| `kas/skomer.yml` | Optional kas build configuration |
| `wic/skomer.wks` | SD layout: FAT boot partition + ext4 rootfs |
| `recipes-kernel/linux/linux-skomer_7.2.bb` | Mainline Linux 7.2 kernel |
| `recipes-kernel/linux/files/defconfig` | Kernel config derived from postmarketOS |
| `recipes-bsp/skomer-bootimg/` | Android v0 boot image for `fastboot boot` |
| `recipes-bsp/skomer-bootscript/` | `boot.scr` for the U-Boot prompt |
| `recipes-core/images/skomer-image-minimal.bb` | Minimal console/SSH image |
| `recipes-core/packagegroups/packagegroup-skomer.bb` | Bring-up tooling |
| `recipes-support/skomer-usb-gadget/` | RNDIS USB networking at `172.16.42.1/24` |
| `scripts/verify-bootimg.sh` | Inspect `skomer-boot.img` with `unpack_bootimg` |

## Requirements

- Yocto **scarthgap** (`poky`)
- `meta-openembedded/meta-oe` (for `mmc-utils`)
- Optional: [kas](https://kas.readthedocs.io/) for a reproducible build

Install the standard host tools required by your Yocto release (compiler,
`make`, `python3`, `git`, `wget`, `chrpath`, `diffstat`, `cpio`, `xz`, `zstd`,
etc.). See the Yocto Project "System Requirements" section for the release and
distribution you are using.

## Build with kas (recommended)

```sh
python -m pip install --user kas

git clone <this-repo> meta-skomer
kas build meta-skomer/kas/skomer.yml
```

Outputs land in `build/tmp/deploy/images/skomer/`.

## Build manually

```sh
mkdir yocto && cd yocto
git clone -b scarthgap https://git.yoctoproject.org/poky
git clone -b scarthgap https://git.openembedded.org/meta-openembedded
git clone <this-repo> meta-skomer

source poky/oe-init-build-env build
bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-skomer
```

Add to `conf/local.conf`:

```conf
MACHINE = "skomer"
INIT_MANAGER = "systemd"
EXTRA_IMAGE_FEATURES = "debug-tweaks"
```

Then build:

```sh
bitbake skomer-image-minimal
```

`debug-tweaks` enables passwordless root login and is intended **only for
bring-up**. Remove it (and set a root password or SSH key) for any real use.

## Build outputs

In `tmp/deploy/images/skomer/`:

| File | Description |
|---|---|
| `skomer-image-minimal-skomer.rootfs.wic` | **SD card image**: MBR + FAT boot partition + ext4 rootfs |
| `skomer-boot.img` | **RAM boot image** for `fastboot boot` |
| `skomer-image-minimal-skomer.rootfs.ext4` | Root filesystem only |
| `skomer-image-minimal-skomer.rootfs.tar.gz` | Root filesystem tarball |
| `zImage` | Kernel |
| `ste-ux500-samsung-skomer.dtb` | Device tree |
| `skomer-noemmc.dtb` | DTB with the eMMC controller disabled (used by the boot image) |
| `boot.scr` | U-Boot script for the SD boot partition |
| `modules-*.tgz` | Kernel modules (also installed in the rootfs) |

### SD layout

The `.wic` image uses `wic/skomer.wks`:

- **p1**: FAT32, label `boot`, 665.6 MiB — `zImage`, `skomer-noemmc.dtb`,
  `boot.scr`
- **p2**: ext4 — root filesystem (135 MiB in the default image, intended to be
  grown to fill the card)

The image does **not** contain U-Boot. The phone's eMMC supplies the stock
U-Boot that reads this card.

## Write the SD card

Use a microSD card you can erase. Check the target device carefully:

```sh
lsblk -o NAME,SIZE,LABEL,MODEL
```

Write the image (replace `/dev/sdX`):

```sh
sudo umount /dev/sdX?* 2>/dev/null

sudo dd if=tmp/deploy/images/skomer/skomer-image-minimal-skomer.rootfs.wic \
        of=/dev/sdX bs=4M conv=fsync status=progress
sync
```

The `.wic` is only ~807 MiB, so grow the root partition to use the rest of the
card:

```sh
sudo growpart /dev/sdX 2
sudo resize2fs /dev/sdX2
sync
```

Check the result before removing the card:

```sh
lsblk -f /dev/sdX
sudo blkid /dev/sdX1 /dev/sdX2   # p1: vfat LABEL="boot"; p2: ext4
sudo e2fsck -fn /dev/sdX2
```

## Boot the phone

### Fastboot (primary)

1. Insert the SD card into the phone.
2. Power the phone off.
3. Hold **Volume Down + Power** to enter U-Boot fastboot.
4. On the host:

```sh
fastboot devices
fastboot boot tmp/deploy/images/skomer/skomer-boot.img
```

`skomer-boot.img` is an Android v0 boot image containing the kernel, the
eMMC-disabled DTB in the `second` area, and a minimal placeholder ramdisk.
It is loaded into RAM; nothing is written to the phone.

The kernel command line is:

```text
console=ttyAMA2,115200 root=/dev/mmcblk0p2 rw rootwait
```

### UART / manual U-Boot prompt

The phone's UART is on the micro-USB port: 619 kOhm from ID to GND and 1.8 V
UART on D+/D-. At the U-Boot prompt:

```text
fatload mmc 1:1 0x18000000 boot.scr
source 0x18000000
```

Or manually:

```text
setenv bootargs console=ttyAMA2,115200 root=/dev/mmcblk0p2 rw rootwait
fatload mmc 1:1 0x00100000 zImage
fatload mmc 1:1 0x01f00000 skomer-noemmc.dtb
bootz 0x00100000 - 0x01f00000
```

UART and USB fastboot share D+/D-, so they cannot be used simultaneously
through the same port.

### Seeing boot messages without UART

The default console is `ttyAMA2`. To also print kernel/systemd messages on the
phone's display, set a command line with `console=tty0` and rebuild the boot
image, e.g. in `local.conf`:

```conf
SKOMER_BOOT_CMDLINE = "console=ttyAMA2,115200 console=tty0 loglevel=8 ignore_loglevel root=/dev/mmcblk0p2 rw rootwait"
```

```sh
bitbake skomer-bootimg
```

## USB networking and SSH

The image starts `skomer-usb-gadget`, which loads `g_ether` and configures
`usb0` on the phone at `172.16.42.1/24`.

On the host:

```sh
ip -brief link                 # find the new interface (usually usb0, or an enp… name)
sudo ip addr add 172.16.42.2/24 dev <interface>
sudo ip link set <interface> up

ssh root@172.16.42.1
```

With `debug-tweaks`, root has an empty password: press Enter at the prompt.

If the interface does not appear:

```sh
lsusb                          # look for "RNDIS/Ethernet Gadget"
dmesg | tail -30
# on the phone (via the display console):
systemctl restart skomer-usb-gadget
lsmod | grep g_ether
```

## Configuration

Machine variables in `conf/machine/skomer.conf`:

| Variable | Default | Meaning |
|---|---|---|
| `SKOMER_DISABLE_EMMC` | `1` | Disable the eMMC controller in the boot DTB so Linux never writes it |
| `SKOMER_EMMC_NODE` | `/soc/mmc@80005000` | DTB node patched by `fdtput` |
| `SKOMER_BOOT_CMDLINE` | `console=ttyAMA2,115200 root=/dev/mmcblk0p2 rw rootwait` | Kernel command line |
| `KERNEL_DEVICETREE` | `st/ste-ux500-samsung-skomer.dtb` | Kernel DTB |
| `SKOMER_BOOTIMG_BASE` | `0x00000000` | Android boot-image base |
| `SKOMER_BOOTIMG_KERNEL_OFFSET` | `0x00008000` | Kernel load offset |
| `SKOMER_BOOTIMG_RAMDISK_OFFSET` | `0x01000000` | Ramdisk load offset |
| `SKOMER_BOOTIMG_SECOND_OFFSET` | `0x00f00000` | DTB (`second`) load offset |
| `SKOMER_BOOTIMG_TAGS_OFFSET` | `0x00000100` | ATAGS offset |
| `SKOMER_BOOTIMG_PAGESIZE` | `2048` | Android boot-image page size |

Set `SKOMER_DISABLE_EMMC = "0"` only for read-only diagnostics, and never
mount eMMC partitions read-write.

## Development notes

### Rebuilding

After kernel, DTB, or machine changes:

```sh
bitbake skomer-image-minimal   # kernel, rootfs, .wic, boot.scr
bitbake skomer-bootimg         # skomer-boot.img (kernel + DTB + ramdisk)
```

If only the kernel or DTB changed, rebuild both the image (for the SD boot
partition) and the boot image (for `fastboot boot`).

### Kernel

`linux-skomer_7.2.bb` uses mainline `linux-7.2.tar.xz` from kernel.org. The
config in `recipes-kernel/linux/files/defconfig` is derived from the
postmarketOS `linux-postmarketos-stericsson` 7.2 configuration, with GCC
adjustments and without debug info or module compression.

To change the config, edit `defconfig` and rebuild. Kernel modules are installed
in the rootfs and included via `kernel-modules`.

### Boot image and the U-Boot ramdisk quirk

The stock U-Boot 2023.04 handles `fastboot boot` by calling `bootm` on the
downloaded image. For v0 Android images, `android_image_get_ramdisk()` returns
an error when `ramdisk_size == 0`; `bootm` then fails and `fastboot_boot()`
calls `do_reset()`, so the phone reboots instead of booting.

`skomer-mkbootimg.py` therefore always embeds a minimal valid `cpio.gz`
containing only the archive trailer and no `/init`. U-Boot accepts the image;
Linux finds no `/init`, falls back to `prepare_namespace()`, and mounts
`root=/dev/mmcblk0p2` normally.

If you replace the placeholder with a real initramfs, make sure the
`/init` script mounts the real root and `switch_root`s to it, or the kernel
will not reach the SD rootfs.

### Device tree

The boot image and the FAT boot partition use `skomer-noemmc.dtb`, produced by
patching the kernel DTB with `fdtput` to set the eMMC controller to
`disabled`. This keeps the read-only eMMC out of Linux's way.

### Adding software

Add packages to `skomer-image-minimal.bb` or
`packagegroup-skomer.bb`, then rebuild and reflash the `.wic`. The minimal image
does not ship a package manager, so installing packages at runtime is not
available by default.

## Verify an image

```sh
# Inspect the Android boot image (requires unpack_bootimg, e.g. android-tools)
sh scripts/verify-bootimg.sh skomer-boot.img

# Inspect the SD image
fdisk -l skomer-image-minimal-skomer.rootfs.wic
7z l skomer-image-minimal-skomer.rootfs.wic       # lists 0.fat and 1.img

# List the FAT boot files (after extracting the first partition)
7z e -so skomer-image-minimal-skomer.rootfs.wic 0.fat > /tmp/boot.fat
7z l /tmp/boot.fat

# Check the rootfs filesystem
dd if=skomer-image-minimal-skomer.rootfs.wic of=/tmp/rootfs.ext4 \
    bs=512 skip=1376256 count=276480 status=none
e2fsck -fn /tmp/rootfs.ext4
```

Expected boot-image layout:

```text
kernel_size: 7926776        kernel load address: 0x00008000
ramdisk size: 44            ramdisk load address: 0x01000000
second bootloader size: 45073   second load address: 0x00f00000
page size: 2048             header version: 0
```

(The exact sizes change when the kernel or DTB changes; the important parts are
a non-empty `ramdisk size` and a non-empty `second` area.)

## What is not included

This layer is a bring-up base, not a daily-driver phone OS. Missing pieces
include:

- modem/RIL, calls, SMS
- WiFi/BT firmware and `wpa_supplicant`/BlueZ integration
- audio (AB8500 codec)
- GPU/Mali and a Weston/Wayland stack
- sensors
- charging and suspend tuning
- a package manager or OTA update mechanism

The console and USB networking work; add the relevant packages and config as
you bring up each subsystem.

## Troubleshooting

- **Phone resets immediately after `fastboot boot`:** the boot image has
  `ramdisk_size == 0`. Use the current `skomer-mkbootimg.py`, which embeds the
  placeholder ramdisk, and rebuild `skomer-bootimg`.
- **Phone shows the penguins, then panics with
  `VFS: Unable to mount root fs on unknown-block(179,2)`:** the SD card's
  second partition is missing or not a valid ext4 filesystem. Rewrite the
  `.wic`, check `blkid`/`e2fsck -fn`, and use a card of the same or larger size.
  Add `console=tty0` to the command line to see the error on the display.
- **`fdtput` fails:** the eMMC node path changed in the DTB. Set
  `SKOMER_EMMC_NODE` accordingly.
- **`zImage`/DTB missing from the boot partition:** check `IMAGE_BOOT_FILES`
  and the deploy directory; rebuild `skomer-image-minimal`.
- **`boot.scr` is not executed:** the stock U-Boot does not scan SD cards. Load
  and `source` it manually, or use fastboot.
- **No USB network interface:** check `lsusb` for the RNDIS gadget, check the
  host interface name with `ip -brief link`, and restart
  `skomer-usb-gadget` on the phone.
- **Root login fails:** `debug-tweaks` must be enabled for the empty password.
  Check `EXTRA_IMAGE_FEATURES` in `local.conf` and rebuild.

## Provenance

- Kernel: mainline `linux-7.2`
  (`https://cdn.kernel.org/pub/linux/kernel/v7.x/linux-7.2.tar.xz`).
- Kernel config: derived from postmarketOS
  `linux-postmarketos-stericsson` 7.2
  (`config-postmarketos-stericsson.armv7`).
- Device tree: mainline
  `arch/arm/boot/dts/st/ste-ux500-samsung-skomer.dts`.
- Boot image format: Android v0 with the DTB in `second`, matching the stock
  U-Boot and the postmarketOS device configuration.

## License

MIT. See `COPYING.MIT`.
