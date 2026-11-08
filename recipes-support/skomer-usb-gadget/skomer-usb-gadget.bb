SUMMARY = "USB RNDIS gadget for Samsung Skomer"
DESCRIPTION = "Brings up the phone's USB gadget as an RNDIS Ethernet interface at 172.16.42.1/24, so SSH works without a UART cable."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://skomer-usb-gadget.sh \
    file://skomer-usb-gadget.service \
"

S = "${WORKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "skomer-usb-gadget.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

RDEPENDS:${PN} = " \
    iproute2 \
    kernel-module-g-ether \
    kernel-module-u-ether \
    kernel-module-libcomposite \
"

do_install() {
    install -d ${D}${sbindir}
    install -d ${D}${systemd_system_unitdir}
    install -m 0755 ${WORKDIR}/skomer-usb-gadget.sh ${D}${sbindir}/skomer-usb-gadget
    install -m 0644 ${WORKDIR}/skomer-usb-gadget.service ${D}${systemd_system_unitdir}/skomer-usb-gadget.service
}
