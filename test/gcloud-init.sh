#!/bin/bash
# Setup the google cloud SDK's bigtable emulator
set -ev

# the only way to install the bigtable emulator is to start it
cat <<EOF | $1/google-cloud-sdk/bin/gcloud beta emulators bigtable start &
Y
EOF

# get the current PID and kill it after a short wait
PID=$!
sleep 30
kill $PID