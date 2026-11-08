#!/bin/sh
# Bring up the legacy g_ether USB gadget on the U8500.
# The pmOS kernel is configured with CONFIG_USB_ETH=m (g_ether), not
# configfs, so this uses the legacy gadget driver.
set -e

modprobe g_ether 2>/dev/null || true

i=0
while [ ! -e /sys/class/net/usb0 ] && [ "$i" -lt 50 ]; do
    sleep 0.1
    i=$((i + 1))
done

if [ -e /sys/class/net/usb0 ]; then
    ip addr add 172.16.42.1/24 dev usb0 2>/dev/null || true
    ip link set usb0 up
fi
