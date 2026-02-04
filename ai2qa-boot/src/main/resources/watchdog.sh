#!/bin/bash

# =============================================================================
# CPU Watchdog - Anti-Crypto-Mining Sidecar
# =============================================================================
# Prevents sustained high CPU usage (abuse) by monitoring usage every 5 seconds.
# Rule: If CPU > 90% for 12 consecutive checks (60s), kill the container.

VIOLATION_COUNT=0
THRESHOLD_PERCENT=90
MAX_VIOLATIONS=12 # 12 * 5s = 60s
CHECK_INTERVAL=5

echo "[Watchdog] Starting CPU monitor..."

while true; do
  # Get CPU usage using top (batch mode, single iteration)
  # grep line with "Cpu(s)", extract user+sys, use awk to start summation
  # Note: Output format depends on `top` version/distro. 
  # In many containers: "%Cpu(s):  0.3 us,  0.3 sy,  0.0 ni, 99.3 id..."
  # We want 100 - idle.
  
  # Using /proc/stat if available is more standard, but user mentioned `top`.
  # Let's try to be robust. 
  
  # Method 1: top (Parsing can be brittle)
  # CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print 100 - $1}')
  
  # Method 2: /sys/fs/cgroup/cpu.stat (Cgroup v2) - user mentioned /sys/fs/cgroup
  # If we have `grep`, `awk`, `bc` we can just read /proc/stat which is standard inside containers too.
  
  # Let's use top as requested but parse carefully.
  # "0.0 id" -> idle percentage.
  # top -bn1 outputs 1 iteration.
  
  # Robust calculation using top:
  # 1. Run top, filter for "Cpu(s)"
  # 2. Extract the idle value (usually before "id")
  # 3. Calculate usage = 100 - idle
  
  # Typical output: %Cpu(s):  5.9 us,  2.1 sy,  0.0 ni, 91.8 id, ...
  # Or: Cpu(s): 0.0%us, 0.0%sy, 0.0%ni, 100.0%id...
  
  IDLE=$(top -bn1 | grep "Cpu(s)" | sed 's/.*, *\([0-9.]*\)%* id.*/\1/')
  
  # If IDLE is empty or not a number, default to 100 (0 usage) to avoid false positives
  if [[ -z "$IDLE" ]]; then
      IDLE=100
  fi
  
  # Calculate Usage
  USAGE=$(echo "100 - $IDLE" | bc -l)
  
  # Check Threshold
  if (( $(echo "$USAGE > $THRESHOLD_PERCENT" | bc -l) )); then
    VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
    echo "[Watchdog] WARNING: High CPU Usage detected: ${USAGE}% (Violation $VIOLATION_COUNT/$MAX_VIOLATIONS)"
  else
    # Reset violations on any dip below threshold (handles spikes)
    if [ "$VIOLATION_COUNT" -gt 0 ]; then
        echo "[Watchdog] CPU Usage normalized: ${USAGE}%. Resetting violation count."
    fi
    VIOLATION_COUNT=0
  fi
  
  # Enforce Penalty
  if [ "$VIOLATION_COUNT" -ge "$MAX_VIOLATIONS" ]; then
    echo "[Watchdog] SECURITY_VIOLATION: Sustained high CPU usage detected (>60s). Terminating container."
    kill 1
    exit 1
  fi
  
  sleep $CHECK_INTERVAL
done
