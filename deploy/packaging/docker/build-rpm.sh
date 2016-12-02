#!/bin/bash
#
# This script will build a single set of rpms for a given configuration
#

# Set a default version
VENDOR_VERSION=apache

if [ ! -z "$BUILD_ARGS" ]; then
	VENDOR_VERSION=$(echo "$BUILD_ARGS" | grep -oi "vendor.version=\w*" | sed "s/vendor.version=//g")
fi
echo "---------------------------------------------------------------"
echo "             Building RPM with the following settings"
echo "---------------------------------------------------------------"
echo "GEOSERVER_VERSION=${GEOSERVER_VERSION}"
echo "BUILD_ARGS=${BUILD_ARGS}"
echo "VENDOR_VERSION=${VENDOR_VERSION}"
echo "---------------------------------------------------------------"
# Ensure mounted volume permissions are OK for access
chown -R root:root /usr/src/geowave/deploy/packaging/rpm

# Staging Artifacts for Build
cd /usr/src/geowave/deploy/packaging/rpm/centos/6/SOURCES
rm -f *.gz *.jar
cp /usr/src/geowave/target/site.tar.gz .
cp /usr/src/geowave/docs/target/manpages.tar.gz .
cp /usr/src/geowave/deploy/target/*.jar .
cp /usr/src/geowave/deploy/target/*.tar.gz .
cd ..

# Build
./rpm.sh --command build --vendor-version $VENDOR_VERSION
