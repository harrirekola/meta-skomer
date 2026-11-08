SUMMARY = "Linux 7.2 kernel for Samsung Skomer (ST-Ericsson U8500)"
DESCRIPTION = "Mainline Linux kernel with the postmarketOS U8500 configuration, adjusted for Yocto (GCC, no debug info, no module compression)."
SECTION = "kernel"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel

SRC_URI = " \
    https://cdn.kernel.org/pub/linux/kernel/v7.x/linux-7.2.tar.xz;name=kernel \
    file://defconfig \
"
SRC_URI[kernel.sha256sum] = "f9fef3d14c0df53819026f4be74459835c2a0b0dcbf5b5bbd9ea19f0829402b3"

S = "${WORKDIR}/linux-7.2"

KERNEL_LOCALVERSION = "-skomer"

COMPATIBLE_MACHINE = "skomer"

# The pmOS config comes from a Clang build. kernel.bbclass copies
# ${WORKDIR}/defconfig to ${B}/.config and olddefconfig() reconciles the
# compiler-dependent symbols for the GCC toolchain used by Yocto.
KERNEL_CONFIG_COMMAND = "oe_runmake_call -C ${S} O=${B} olddefconfig || oe_runmake -C ${S} O=${B} oldnoconfig"
