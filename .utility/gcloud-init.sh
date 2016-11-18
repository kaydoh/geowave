#!/bin/bash
# Download the google cloud SDK
wget https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-135.0.0-linux-x86_64.tar.gz
gunzip google-cloud-sdk-135.0.0-linux-x86_64.tar.gz
tar xvf google-cloud-sdk-135.0.0-linux-x86_64.tar
rm google-cloud-sdk-135.0.0-linux-x86_64.tar

# install the beta component & emulator
cat <<EOF | ./google-cloud-sdk/bin/gcloud components install beta
Y
EOF

cat <<EOF | ./google-cloud-sdk/bin/gcloud beta emulators bigtable env-init --quiet
Y
EOF

# start the emulator
./google-cloud-sdk/bin/gcloud beta emulators bigtable start &

# wait a few seconds
sleep 10

# get the emulator port and set it in the env
./google-cloud-sdk/bin/gcloud beta emulators bigtable env-init > exportBigtableEnv

# this next step has to be run outside this script:
source exportBigtableEnv