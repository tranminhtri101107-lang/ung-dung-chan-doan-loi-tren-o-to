#!/bin/bash
# Dừng ECU giả lập và Gateway đã chạy bằng chay-demo.sh
pkill -x gateway 2>/dev/null && echo "Đã dừng Gateway" || echo "Gateway không chạy"
pkill -x ecu_engine 2>/dev/null && echo "Đã dừng ECU" || echo "ECU không chạy"
