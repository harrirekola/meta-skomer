SUMMARY = "Minimal Yocto image for Samsung Skomer"
DESCRIPTION = "Minimal console image for the Samsung Galaxy Xcover 2 (skomer). Boots from SD; the phone's eMMC is not written."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

require recipes-core/images/core-image-minimal.bb

IMAGE_INSTALL:append = " packagegroup-skomer kernel-modules"
IMAGE_FEATURES:append = " ssh-server-dropbear"

# For a first bring-up image the serial console on ttyAMA2 is the primary
# interface. The optional skomer-usb-gadget package provides RNDIS USB
# networking at 172.16.42.1/24.
