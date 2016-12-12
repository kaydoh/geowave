#!/bin/bash
#
# This script will build a single set of rpms for a given configuration
#

# This script runs with a volume mount to $WORKSPACE, this ensures that any signal failure will leave all of the files $WORKSPACE editable by the host  
trap 'chmod -R 777 $WORKSPACE/deploy/packaging/rpm' EXIT
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
echo "BUILD_SUFFIX=${BUILD_SUFFIX}"
echo "TIME_TAG=${TIME_TAG}"
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
if [ $BUILD_SUFFIX = "common" ]
then
	rm -f *.gz *.jar
	cp /usr/src/geowave/target/site-${GEOWAVE_VERSION}.tar.gz .
	cp /usr/src/geowave/docs/target/manpages-${GEOWAVE_VERSION}.tar.gz .
	cp /usr/src/geowave/deploy/target/*${GEOWAVE_VERSION}.tar.gz .
else
	rm -f *.gz *.jar
	if [[ ! -f ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-accumulo-to-hdfs.sh ]]; then
		# Copy the template for accumulo to sources
		cp ${WORKSPACE}/deploy/packaging/docker/build-rpm/deploy-geowave-to-hdfs.sh.template ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-accumulo-to-hdfs.sh
	
		# Replace the tokens appropriately for accumulo
		sed -i -e s/'$DATASTORE_TOKEN'/accumulo/g ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-accumulo-to-hdfs.sh
		sed -i -e s/'$DATASTORE_USER_TOKEN'/accumulo/g ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-accumulo-to-hdfs.sh
	fi

	if [[ ! -f ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-hbase-to-hdfs.sh ]]; then
		# Copy the template for hbase to sources
		cp ${WORKSPACE}/deploy/packaging/docker/build-rpm/deploy-geowave-to-hdfs.sh.template ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-hbase-to-hdfs.sh
	
		# Replace the tokens appropriately for hbase
		sed -i -e s/'$DATASTORE_TOKEN'/hbase/g ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-hbase-to-hdfs.sh
		sed -i -e s/'$DATASTORE_USER_TOKEN'/hbase/g ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/deploy-geowave-hbase-to-hdfs.sh
	fi
	cp /usr/src/geowave/deploy/target/*${GEOWAVE_VERSION}-${VENDOR_VERSION}.jar .
	cp /usr/src/geowave/deploy/target/*${GEOWAVE_VERSION}-${VENDOR_VERSION}.tar.gz .
fi
cd ..

# Build
$WORKSPACE/deploy/packaging/rpm/centos/6/rpm.sh --command build-${BUILD_SUFFIX} --vendor-version $VENDOR_VERSION --geowave-version $GEOWAVE_VERSION --time-tag $TIME_TAG