#!/bin/bash
# Get the PID for the emulator
pid=$(ps -ef | grep "[g]cloud beta"  | awk '{print $2}')
echo $pid

# Kill child processes in the group
pgrep -g $pid | xargs -L1 kill -9
