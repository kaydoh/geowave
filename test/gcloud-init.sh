#!/bin/bash
# Download the google cloud SDK
wget https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-136.0.0-linux-x86_64.tar.gz
gunzip google-cloud-sdk-136.0.0-linux-x86_64.tar.gz
tar xvf google-cloud-sdk-136.0.0-linux-x86_64.tar
rm google-cloud-sdk-136.0.0-linux-x86_64.tar

# install the beta component
cat <<EOF | ./google-cloud-sdk/bin/gcloud components install beta
Y
EOF

# the only way to install the bigtable emulator is to start it
cat <<EOF | ./google-cloud-sdk/bin/gcloud beta emulators bigtable start &
Y
EOF

# get the current PID and kill it after a short wait
PID=$!
sleep 30
kill $PID

# set the emulator host:port in the env
export BIGTABLE_EMULATOR_HOST=localhost:8128

# start the emulator
./google-cloud-sdk/bin/gcloud beta emulators bigtable start --host-port localhost:8128 &
