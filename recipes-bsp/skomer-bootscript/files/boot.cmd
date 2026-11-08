# Skomer U-Boot boot script.
#
# The stock U-Boot does not automatically boot from SD. At the U-Boot
# prompt, load this script from the FAT boot partition and source it:
#
#   fatload mmc 1:1 0x18000000 boot.scr
#   source 0x18000000
#
# The SD is mmc 1 in U-Boot and /dev/mmcblk0 in Linux. Partition 1 is the
# FAT boot partition; partition 2 is the ext4 rootfs.

setenv bootargs console=ttyAMA2,115200 root=/dev/mmcblk0p2 rw rootwait

fatload mmc 1:1 0x00100000 zImage
fatload mmc 1:1 0x01f00000 skomer-noemmc.dtb

# No initramfs: the kernel has the MMC and ext4 drivers built in.
bootz 0x00100000 - 0x01f00000
