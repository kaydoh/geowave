#!/bin/bash
#
# This script will build and package all of the configurations listed in the BUILD_ARGS_MATRIX array.
#
# Source all our reusable functionality, argument is the location of this script.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/../../.."
WORKSPACE="$(pwd)"
DOCKER_ROOT=$WORKSPACE/docker-root
SKIP_EXTRA="-Dfindbugs.skip -Dformatter.skip -DskipTests"
GEOSERVER_VERSION=geoserver-2.10.0-bin.zip
GEOSERVER_ARTIFACT=$WORKSPACE/deploy/packaging/rpm/centos/6/SOURCES/geoserver.zip

if [ -z $GEOSERVER_DOWNLOAD_BASE ]; then
	GEOSERVER_DOWNLOAD_BASE=https://s3.amazonaws.com/geowave-deploy/third-party-downloads/geoserver
fi

if [ -z $GEOSERVER_VERSION ]; then
	GEOSERVER_VERSION=geoserver-2.10.0-bin.zip
fi

if [ ! -f "$GEOSERVER_ARTIFACT" ]; then
	curl $GEOSERVER_DOWNLOAD_BASE/$GEOSERVER_VERSION > $GEOSERVER_ARTIFACT
fi

# If you'd like to build a different set of artifacts rename build-args-matrix.sh.example
if [ -f $SCRIPT_DIR/build-args-matrix.sh ]; then
	source $SCRIPT_DIR/build-args-matrix.sh
else
	# Default build arguments
    BUILD_ARGS_MATRIX=(
 "-Daccumulo.version=1.7.2 -Daccumulo.api=1.7 -Dhadoop.version=2.7.3 -Dgeotools.version=16.0 -Dgeoserver.version=2.10.0 -Dhbase.version=1.2.3 -Dvendor.version=apache"
 "-Daccumulo.version=1.7.2-cdh5.5.0 -Daccumulo.api=1.7 -Dhadoop.version=2.6.0-cdh5.9.0 -Dgeotools.version=16.0 -Dgeoserver.version=2.10.0 -Dhbase.version=1.2.0-cdh5.9.0 -P cloudera -Dvendor.version=cdh5"
 "-Daccumulo.version=1.7.0.2.4.2.0-258 -Daccumulo.api=1.7 -Dhadoop.version=2.7.1.2.4.2.0-258 -Dgeotools.version=16.0 -Dgeoserver.version=2.10.0 -Dhbase.version=1.1.2.2.4.2.0-258 -P hortonworks -Dvendor.version=hdp2"
    )
fi

export MVN_PACKAGE_VENDOR_CMD="/usr/src/geowave/deploy/packaging/docker/build-geowave-vendor.sh $SKIP_EXTRA"
export MVN_PACKAGE_COMMON_CMD="/usr/src/geowave/deploy/packaging/docker/build-geowave-common.sh $SKIP_EXTRA"
mkdir $DOCKER_ROOT

$WORKSPACE/deploy/packaging/docker/pull-s3-caches.sh $DOCKER_ROOT
$WORKSPACE/deploy/packaging/rpm/centos/6/rpm.sh --command clean
	
docker run --rm \
	-e WORKSPACE=/usr/src/geowave \
	-e MAVEN_OPTS="-Xmx1500m" \
	-v $DOCKER_ROOT:/root -v $WORKSPACE:/usr/src/geowave \
	ngageoint/geowave-centos6-java8-build \
	/bin/bash -c \
	"chmod -R 777 \$WORKSPACE && cd \$WORKSPACE && $MVN_PACKAGE_COMMON_CMD && chmod -R 777 \$WORKSPACE"
	
docker run --rm \
    -e WORKSPACE=/usr/src/geowave \
	-e GEOSERVER_VERSION="$GEOSERVER_VERSION" \
	-e BUILD_TYPE="common" \
    -v $DOCKER_ROOT:/root -v $WORKSPACE:/usr/src/geowave \
    ngageoint/geowave-centos6-rpm-build \
    /bin/bash -c \
    "cd \$WORKSPACE && deploy/packaging/docker/build-rpm.sh  && chmod -R 777 \$WORKSPACE/deploy/packaging/rpm"
		
for build_args in "${BUILD_ARGS_MATRIX[@]}"
do
	export BUILD_ARGS="$build_args"
	
	docker run --rm \
		-e WORKSPACE=/usr/src/geowave \
		-e BUILD_ARGS="$build_args" \
		-e MAVEN_OPTS="-Xmx1500m" \
		-v $DOCKER_ROOT:/root -v $WORKSPACE:/usr/src/geowave \
		ngageoint/geowave-centos6-java8-build \
		/bin/bash -c \
		"chmod -R 777 \$WORKSPACE && cd \$WORKSPACE && $MVN_PACKAGE_VENDOR_CMD && chmod -R 777 \$WORKSPACE"

	docker run --rm \
    	-e WORKSPACE=/usr/src/geowave \
    	-e BUILD_ARGS="$build_args" \
		-e GEOSERVER_VERSION="$GEOSERVER_VERSION" \
		-e BUILD_TYPE="vendor" \
    	-v $DOCKER_ROOT:/root -v $WORKSPACE:/usr/src/geowave \
    	ngageoint/geowave-centos6-rpm-build \
    	/bin/bash -c \
    	"cd \$WORKSPACE && deploy/packaging/docker/build-rpm.sh  && chmod -R 777 \$WORKSPACE/deploy/packaging/rpm"
done
