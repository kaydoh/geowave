#!/bin/bash
#
# This script will build a single set of rpms for a given configuration
#

# Set a default version
VENDOR_VERSION=apache

if [ ! -z "$BUILD_ARGS" ]; then
	VENDOR_VERSION=$(echo "$BUILD_ARGS" | grep -oi "vendor.version=\w*" | sed "s/vendor.version=//g")
fi
# Get the version
GEOWAVE_VERSION=$(cat $WORKSPACE/deploy/target/version.txt)

echo "---------------------------------------------------------------"
echo "             Building RPM with the following settings"
echo "---------------------------------------------------------------"
echo "GEOWAVE_VERSION=${GEOWAVE_VERSION}"
echo "BUILD_TYPE=${BUILD_TYPE}"
echo "GEOSERVER_VERSION=${GEOSERVER_VERSION}"
echo "BUILD_ARGS=${BUILD_ARGS}"
echo "VENDOR_VERSION=${VENDOR_VERSION}"
echo "---------------------------------------------------------------"
# Ensure mounted volume permissions are OK for access
chown -R root:root $WORKSPACE/deploy/packaging/rpm

# Now make sure the host can easily modify/delete generated artifacts
chmod -R 777 $WORKSPACE/deploy/packaging/rpm

# Staging Artifacts for Build
cd $WORKSPACE/deploy/packaging/rpm/centos/6/SOURCES
if [ $BUILD_TYPE = "common" ]
then
	rm -f *.gz *.jar
	cp /usr/src/geowave/target/site.tar.gz .
	cp /usr/src/geowave/docs/target/manpages.tar.gz .
else
	cp /usr/src/geowave/deploy/target/*.jar .
fi
# both builds have tar.gz
cp /usr/src/geowave/deploy/target/*.tar.gz .
cd ..

# Build
$WORKSPACE/deploy/packaging/rpm/centos/6/rpm.sh --command build-${BUILD_TYPE} --vendor-version $VENDOR_VERSION --geowave-version $GEOWAVE_VERSION --time-tag $TIME_TAG