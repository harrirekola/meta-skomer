SUMMARY = "U-Boot boot script for Skomer SD cards"
DESCRIPTION = "Compiles a boot.cmd that loads zImage and the skomer DTB from the FAT boot partition and boots it with bootz."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS = "u-boot-tools-native"
inherit deploy

SRC_URI = "file://boot.cmd"

S = "${WORKDIR}"
B = "${WORKDIR}/build"

do_compile() {
    install -d ${B}
    mkimage -A arm -O linux -T script -C none -n "skomer boot" \
        -d ${WORKDIR}/boot.cmd ${B}/boot.scr
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/boot.scr ${DEPLOYDIR}/boot.scr
}
addtask deploy after do_compile before do_build
