#!/bin/bash
sudo modprobe vcan
sudo ip link add dev vcan0 type vcan 2>/dev/null
sudo ip link set up vcan0
echo "vcan0 đã sẵn sàng"