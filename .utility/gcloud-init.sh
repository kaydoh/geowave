#!/bin/bash
set -v

if [ "$STORE_TYPE" == "BIGTABLE" ]; then
	if [ ! -d ./google-cloud-sdk ]; then
		# Download the google cloud SDK
		wget https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-135.0.0-linux-x86_64.tar.gz
		gunzip google-cloud-sdk-135.0.0-linux-x86_64.tar.gz
		tar xvf google-cloud-sdk-135.0.0-linux-x86_64.tar
		rm google-cloud-sdk-135.0.0-linux-x86_64.tar
	fi

	# install the beta component & emulator
	cat <<EOF | ./google-cloud-sdk/bin/gcloud components install beta
Y
EOF

	cat <<EOF | ./google-cloud-sdk/bin/gcloud beta emulators bigtable env-init --quiet
Y
EOF

	# run the following steps after this script completes:
	# start the emulator
	#./google-cloud-sdk/bin/gcloud beta emulators bigtable start &

	# get the emulator port and set it in the env
	#./google-cloud-sdk/bin/gcloud beta emulators bigtable env-init > exportBigtableEnv

	# source exportBigtableEnv
fi