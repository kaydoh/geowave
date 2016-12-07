#!/bin/bash
#
# For use by rpm building jenkins jobs. Handles job race conditions and
# reindexing the existing rpm repo
#

# Set a default version
BUILD_NUMBER=$(cat $WORKSPACE/deploy/target/buildnumber.txt)
# Get the version
GEOWAVE_VERSION=$(cat $WORKSPACE/deploy/target/version.txt)
VENDOR_VERSION=apache

if [ ! -z "$BUILD_ARGS" ]; then
	VENDOR_VERSION=$(echo "$BUILD_ARGS" | grep -oi "vendor.version=\w*" | sed "s/vendor.version=//g")
fi
echo "---------------------------------------------------------------"
echo "         Publishing GeoWave Vendor-specific RPMs"
echo "GEOWAVE_VERSION=${GEOWAVE_VERSION}"
echo "BUILD_NUMBER=${BUILD_NUMBER}"
echo "VENDOR_VERSION=${VENDOR_VERSION}"
echo "BUILD_ARGS=${BUILD_ARGS}"
echo "---------------------------------------------------------------"
set -x
echo '###### Build Variables'

declare -A ARGS
while [ $# -gt 0 ]; do
    # Trim the first two chars off of the arg name ex: --foo
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done
echo '###### Build tarball distribution archive'

# Copy the SRPM into an extract directory
mkdir -p ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave
cp ${WORKSPACE}/${ARGS[buildroot]}/SRPMS/*.rpm ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave
cd ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave

# Extract all the files
rpm2cpio *.rpm | cpio -idmv

# Remove what we don't want to distribute within the tarball
rm -f *.rpm *.xml *.spec

# Extract the build metadata from one of the artifacts
unzip -p geowave-accumulo-${GEOWAVE_VERSION}-${VENDOR_VERSION}.jar build.properties > build.properties

# Archive things, copy some artifacts up to AWS if available and get rid of our temp area
cd ..
tar cvzf geowave-${GEOWAVE_VERSION}-${VENDOR_VERSION}-${BUILD_NUMBER:0:7}.tar.gz geowave

rm -rf geowave

# Copy the Jace artifacts
cp ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/geowave-jace-${GEOWAVE_VERSION}-${VENDOR_VERSION}.tar.gz ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave-jace-${VENDOR_VERSION}-${GEOWAVE_VERSION}-${BUILD_NUMBER:0:7}.tar.gz

echo '###### Copy rpm to repo and reindex'

LOCAL_REPO_DIR=/var/www/html/repos/snapshots
cp -R ${WORKSPACE}/${ARGS[buildroot]}/RPMS/${ARGS[arch]}/*.rpm ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/${ARGS[arch]}/
cp -fR ${WORKSPACE}/${ARGS[buildroot]}/SRPMS/*.rpm ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/SRPMS/
cp -fR ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/*.tar.gz ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/TARBALL/
if [ ${ARGS[buildtype]} = "dev" ]
then
	NOW=$(date +"%Y%m%d%H%M")
	pushd ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/
	for i in *.jar; do cp "${i}" ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}-jars/JAR/"${i%.jar}-${NOW}.jar" ; done
	popd
else	
	cp -fR ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/*.jar ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}-jars/JAR/
fi

# When several processes run createrepo concurrently they will often fail with problems trying to
# access index files that are in the process of being overwritten by the other processes. The command
# below uses two utilities that will cause calls to createrepo (from this script) to wait to gain an
# exclusive file lock before proceeding with a maximum wait time set at 10 minutes before they give
# up and fail. the ha* commands are from the hatools rpm available via EPEL.
/usr/bin/hatimerun -t 10:00 \
/usr/bin/halockrun /var/lock/subsys/rpmrepo \
/usr/bin/createrepo --update --workers 2 ${SNAPSHOT_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/${ARGS[arch]}/