SUMMARY = "Extra packages for the Samsung Skomer image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit packagegroup

RDEPENDS:${PN} = " \
    mmc-utils \
    e2fsprogs \
    dosfstools \
    util-linux \
    usbutils \
    iproute2 \
    dtc \
    kernel-modules \
    skomer-usb-gadget \
    skomer-firmware \
    iw \
    wpa-supplicant \
    wireless-regdb-static \
    bluez5 \
"

# Optional, add these to the image when you start working on the
# corresponding hardware:
#   alsa-utils
#   weston weston-init
