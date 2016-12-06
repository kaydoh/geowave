#!/bin/bash
#
# GeoWave Jenkins Build Script
#

echo "---------------------------------------------------------------"
echo "         Building GeoWave with the following settings"
echo "---------------------------------------------------------------"
echo "BUILD_ARGS=${BUILD_ARGS} ${@}"
echo "---------------------------------------------------------------"
# Set a default version
VENDOR_VERSION=apache

if [ ! -z "$BUILD_ARGS" ]; then
	VENDOR_VERSION=$(echo "$BUILD_ARGS" | grep -oi "vendor.version=\w*" | sed "s/vendor.version=//g")
fi
# Throughout the build, capture jace artifacts to support testing
mkdir -p $WORKSPACE/deploy/target/geowave-jace/bin

# Build each of the "fat jar" artifacts and rename to remove any version strings in the file name

mvn package -am -pl deploy -P geotools-container-singlejar -Dgeotools.finalName=geowave-geoserver-${VENDOR_VERSION} $BUILD_ARGS "$@"

mvn package -am -pl deploy -P accumulo-container-singlejar -Daccumulo.finalName=geowave-accumulo-${VENDOR_VERSION} $BUILD_ARGS "$@"

mvn package -am -pl deploy -P hbase-container-singlejar -Dhbase.finalName=geowave-hbase-${VENDOR_VERSION} $BUILD_ARGS "$@"

mvn package -am -pl deploy -P geowave-tools-singlejar -Dtools.finalName=geowave-tools-${VENDOR_VERSION} $BUILD_ARGS "$@"

# Copy the tools fat jar
cp $WORKSPACE/deploy/target/geowave-tools-${VENDOR_VERSION}.jar $WORKSPACE/deploy/target/geowave-jace/bin/geowave-tools-${VENDOR_VERSION}.jar

# Run the Jace hack
cd $WORKSPACE
chmod +x $WORKSPACE/deploy/packaging/docker/install-jace.sh
$WORKSPACE/deploy/packaging/docker/install-jace.sh $BUILD_ARGS "$@"

cd $WORKSPACE
# Build the jace bindings
if [ ! -f $WORKSPACE/deploy/target/geowave-jace-${VENDOR_VERSION}.tar.gz ]; then
	rm -rf $WORKSPACE/deploy/target/dependency
	mvn package -am -pl deploy -P generate-geowave-jace -Djace.finalName=geowave-jace-${VENDOR_VERSION} $BUILD_ARGS "$@"
    mv $WORKSPACE/deploy/target/geowave-jace-${VENDOR_VERSION}.jar $WORKSPACE/deploy/target/geowave-jace/bin/geowave-runtime-${VENDOR_VERSION}.jar
    cp $WORKSPACE/deploy/jace/CMakeLists.txt $WORKSPACE/deploy/target/geowave-jace
    cp -R $WORKSPACE/deploy/target/dependency/jace/source $WORKSPACE/deploy/target/geowave-jace
    cp -R $WORKSPACE/deploy/target/dependency/jace/include $WORKSPACE/deploy/target/geowave-jace
	tar -czf $WORKSPACE/deploy/target/geowave-jace-${VENDOR_VERSION}.tar.gz -C $WORKSPACE/deploy/target/*${VENDOR_VERSION}.jar geowave-jace
fi
