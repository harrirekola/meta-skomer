SUMMARY = "Android v0 boot image for Samsung Skomer"
DESCRIPTION = "Wraps the Yocto kernel and a modified skomer DTB in the v0 Android boot-image format used by the stock U-Boot. The DTB lives in the second area, which U-Boot 2023.04 passes to the kernel."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

DEPENDS = "dtc-native python3-native"
inherit deploy python3native

SRC_URI = "file://skomer-mkbootimg.py"

S = "${WORKDIR}"
B = "${WORKDIR}/build"

SKOMER_DTB ?= "${KERNEL_DEVICETREE}"

do_compile[depends] += "virtual/kernel:do_deploy"

do_compile() {
    install -d ${B}

    DTB_IN=""
    for candidate in \
        "${DEPLOY_DIR_IMAGE}/${SKOMER_DTB}" \
        "${DEPLOY_DIR_IMAGE}/$(basename ${SKOMER_DTB})"; do
        if [ -f "$candidate" ]; then
            DTB_IN="$candidate"
            break
        fi
    done
    if [ -z "$DTB_IN" ]; then
        echo "ERROR: DTB not found: ${SKOMER_DTB}" >&2
        echo "Looked in ${DEPLOY_DIR_IMAGE}" >&2
        exit 1
    fi

    cp "$DTB_IN" ${B}/skomer.dtb
    if [ "${SKOMER_DISABLE_EMMC}" = "1" ]; then
        fdtput -t s ${B}/skomer.dtb "${SKOMER_EMMC_NODE}" status disabled
    fi

    ${PYTHON} ${WORKDIR}/skomer-mkbootimg.py \
        --kernel ${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE} \
        --dtb ${B}/skomer.dtb \
        --cmdline "${SKOMER_BOOT_CMDLINE}" \
        --base ${SKOMER_BOOTIMG_BASE} \
        --kernel-offset ${SKOMER_BOOTIMG_KERNEL_OFFSET} \
        --ramdisk-offset ${SKOMER_BOOTIMG_RAMDISK_OFFSET} \
        --second-offset ${SKOMER_BOOTIMG_SECOND_OFFSET} \
        --tags-offset ${SKOMER_BOOTIMG_TAGS_OFFSET} \
        --page-size ${SKOMER_BOOTIMG_PAGESIZE} \
        --output ${B}/skomer-boot.img
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0644 ${B}/skomer-boot.img ${DEPLOYDIR}/skomer-boot.img
    install -m 0644 ${B}/skomer.dtb ${DEPLOYDIR}/skomer-noemmc.dtb
}
addtask deploy after do_compile before do_build
