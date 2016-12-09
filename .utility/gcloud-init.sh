#!/bin/bash
set -v

if [ "$STORE_TYPE" == "BIGTABLE" ]; then
	if [ ! -d ./google-cloud-sdk ]; then
		# Download the google cloud SDK
		wget https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-136.0.0-linux-x86_64.tar.gz
		gunzip google-cloud-sdk-136.0.0-linux-x86_64.tar.gz
		tar xvf google-cloud-sdk-136.0.0-linux-x86_64.tar
		rm google-cloud-sdk-136.0.0-linux-x86_64.tar

		# install the beta component & emulator
		cat <<EOF | ./google-cloud-sdk/bin/gcloud components install beta
Y
EOF

		cat <<EOF | ./google-cloud-sdk/bin/gcloud beta emulators bigtable start &
Y
EOF

		# get the current PID and kill it after a short wait
		PID=$!
		sleep 30
		kill $PID
	fi
	
	# run the following steps after this script completes:
	# start the emulator
	#./google-cloud-sdk/bin/gcloud beta emulators bigtable start --host-port localhost:8128 &

	# get the emulator port and set it in the env
	# export BIGTABLE_EMULATOR_HOST=localhost:8128
fi