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
| `recipes-support/skomer-firmware/` | Broadcom BCM4334 WiFi firmware and board NVRAM |
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

## WiFi

The image includes the BCM4334 firmware/NVRAM
(`recipes-support/skomer-firmware/`) plus `iw`, `wpa-supplicant` and
`wireless-regdb-static`.

Check that the SDIO card and driver came up:

```sh
dmesg | grep -iE 'mmc1|brcmfmac|firmware'
ip -brief link
```

Connect manually:

```sh
wpa_passphrase 'YourSSID' 'YourPassword' >> /etc/wpa_supplicant.conf
wpa_supplicant -B -i wlan0 -c /etc/wpa_supplicant.conf -D nl80211
wpa_cli -i wlan0 status
udhcpc -i wlan0
ping -c 3 1.1.1.1
```

`wpa_state=COMPLETED` means association succeeded. `wpa_supplicant` handles
the wireless link; `udhcpc` obtains the IP address, route and DNS.

For boot-time persistence, create
`/etc/wpa_supplicant/wpa_supplicant-wlan0.conf` with the same
`ctrl_interface` and `network` settings and enable
`wpa_supplicant@wlan0.service`. Add DHCP through `systemd-networkd` or a small
`udhcpc` unit.

The NVRAM template MAC is randomized by `brcmfmac`; provision a fixed address
if you need a stable one. The firmware/NVRAM are proprietary
(`LICENSE = "CLOSED"`); do not publish them without checking redistribution
rights.

If `wlan0` does not appear, check `dmesg | grep -i brcmfmac` and confirm both
firmware files are present in `/lib/firmware/brcm/`. `wpa_cli` needs
`-i wlan0`; without it you will see `(nil)`. If you replace
`/etc/wpa_supplicant.conf` with `wpa_passphrase` output, keep
`ctrl_interface=/var/run/wpa_supplicant` or `wpa_cli` cannot connect.

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

Rebuild after kernel, DTB, or machine changes:

```sh
bitbake skomer-image-minimal
bitbake skomer-bootimg
```

- **Kernel:** `linux-skomer_7.2.bb` uses mainline 7.2. Edit
  `recipes-kernel/linux/files/defconfig` to change the configuration.
- **Boot image:** `skomer-mkbootimg.py` embeds a minimal `cpio.gz` because the
  stock U-Boot rejects v0 images with `ramdisk_size == 0`. A real initramfs
  must mount the real root and `switch_root` to it.
- **Device tree:** `skomer-noemmc.dtb` is the kernel DTB with the eMMC node
  disabled via `fdtput`.
- **Adding software:** add packages to `packagegroup-skomer.bb` or
  `skomer-image-minimal.bb`; the image does not ship a package manager.

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
- Bluetooth firmware and BlueZ integration
- automatic WiFi provisioning (NetworkManager/systemd-networkd)
- audio (AB8500 codec)
- GPU/Mali and a Weston/Wayland stack
- sensors
- charging and suspend tuning
- a package manager or OTA update mechanism

The console, USB networking, and manual WiFi work; add the relevant packages
and config as you bring up each subsystem. See [WiFi](#wifi) for the current
WiFi workflow.

## Troubleshooting

- **Phone resets after `fastboot boot`:** `ramdisk_size == 0`; use the current
  `skomer-mkbootimg.py` and rebuild `skomer-bootimg`.
- **`VFS: Unable to mount root fs ...`:** the SD rootfs is missing or invalid.
  Rewrite the `.wic`, check `blkid`/`e2fsck -fn`, and add `console=tty0` to see
  the error on the display.
- **`fdtput` fails:** the eMMC node path changed; set `SKOMER_EMMC_NODE`.
- **`zImage`/DTB missing from the boot partition:** check `IMAGE_BOOT_FILES`
  and rebuild `skomer-image-minimal`.
- **`boot.scr` is not executed:** stock U-Boot does not scan SD cards; load and
  `source` it manually, or use fastboot.
- **No USB network interface:** check `lsusb` and `ip -brief link`, then restart
  `skomer-usb-gadget` on the phone.
- **Root login fails:** `debug-tweaks` must be enabled.

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
