SUMMARY = "Broadcom BCM4334 firmware for Samsung Skomer"
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "skomer"
PACKAGE_ARCH = "${MACHINE_ARCH}"

INHIBIT_DEFAULT_DEPS = "1"

UNPACKDIR = "${WORKDIR}"

SRC_URI = " \
    file://brcmfmac4334-sdio.samsung,skomer.bin \
    file://brcmfmac4334-sdio.samsung,skomer.txt \
    file://BCM4334B0.hcd \
"

do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware/brcm
    install -m 0644 \
        ${UNPACKDIR}/brcmfmac4334-sdio.samsung,skomer.bin \
        ${UNPACKDIR}/brcmfmac4334-sdio.samsung,skomer.txt \
        ${UNPACKDIR}/BCM4334B0.hcd \
        ${D}${nonarch_base_libdir}/firmware/brcm/
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/brcm"

# Prevent QA errors on prebuilt binary blobs
INSANE_SKIP:${PN} += "already-stripped"