#!/bin/bash
#
# This script will build and package all of the configurations listed in teh BUILD_ARGS_MATRIX array.
#

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/../../.."
WORKSPACE="$(pwd)"
DOCKER_ROOT=$WORKSPACE/docker-root
SKIP_EXTRA="-Dfindbugs.skip -Dformatter.skip -DskipTests"
GEOSERVER_VERSION=geoserver-2.10.0-bin.zip
# selinux config if needed
type getenforce >/dev/null 2>&1 && getenforce >/dev/null 2>&1 && chcon -Rt svirt_sandbox_file_t $WORKSPACE;

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

export MVN_PACKAGE_FAT_JARS_CMD="/usr/src/geowave/deploy/packaging/rpm/admin-scripts/jenkins-build-geowave.sh $SKIP_EXTRA"
mkdir $DOCKER_ROOT

$WORKSPACE/deploy/packaging/docker/pull-s3-caches.sh $DOCKER_ROOT
$WORKSPACE/deploy/packaging/rpm/centos/6/rpm.sh --command clean

for build_args in "${BUILD_ARGS_MATRIX[@]}"
do
	export BUILD_ARGS="$build_args"
	
	docker run --rm \
		-e WORKSPACE=/usr/src/geowave \
		-e BUILD_ARGS="$build_args" \
		-e MAVEN_OPTS="-Xmx1500m" \
		-e LOCAL_USER_ID="$(whoami)" \
		-v $DOCKER_ROOT:/root -v $WORKSPACE:/usr/src/geowave \
		ngageoint/geowave-centos6-java8-build \
		/bin/bash -c \
		"cd \$WORKSPACE && $MVN_PACKAGE_FAT_JARS_CMD && chown -R $LOCAL_USER_ID $WORKSPACE"

	docker run --rm \
    	-e WORKSPACE=/usr/src/geowave \
    	-e BUILD_ARGS="$build_args" \
		-e LOCAL_USER_ID="$(whoami)" \
		-e GEOSERVER_VERSION="$GEOSERVER_VERSION" \
    	-v $DOCKER_ROOT:/root -v $WORKSPACE:/usr/src/geowave \
    	ngageoint/geowave-centos6-rpm-build \
    	/bin/bash -c \
    	"cd \$WORKSPACE && deploy/packaging/docker/build-rpm.sh && chown -R $LOCAL_USER_ID $WORKSPACE/deploy/packaging"
done
