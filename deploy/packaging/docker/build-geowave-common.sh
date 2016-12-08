#!/bin/bash
#
# GeoWave Common Build Script
#

# This script runs with a volume mount to $WORKSPACE, this ensures that any signal failure will leave all of the files $WORKSPACE editable by the host  
trap 'chmod -R 777 $WORKSPACE' EXIT

echo "---------------------------------------------------------------"
echo "         Building GeoWave Common"
echo "---------------------------------------------------------------"
mkdir -p $WORKSPACE/deploy/target
GEOWAVE_VERSION_STR="$(mvn -q -o -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive -f $WORKSPACE/pom.xml exec:exec)"
echo ${GEOWAVE_VERSION_STR} | sed -e 's/"//g' -e 's/-SNAPSHOT//g' > $WORKSPACE/deploy/target/version.txt
if [ $GEOWAVE_VERSION_STR == *"-SNAPSHOT"* ]
then
	#its a dev/latest build
	echo "dev" > $WORKSPACE/deploy/target/build-type.txt
	echo "latest" > $WORKSPACE/deploy/target/version-url.txt
else
	#its a release
	echo "release" > $WORKSPACE/deploy/target/build-type.txt
	echo $GEOWAVE_VERSION_STR > $WORKSPACE/deploy/target/version-url.txt
fi
# Build and archive HTML/PDF docs
if [ ! -f $WORKSPACE/target/site.tar.gz ]; then
    mvn -o javadoc:aggregate $BUILD_ARGS "$@"
    mvn -o -P docs -pl docs install $BUILD_ARGS "$@"
    tar -czf $WORKSPACE/target/site.tar.gz -C $WORKSPACE/target site
fi

# Build and archive the man pages
if [ ! -f $WORKSPACE/docs/target/manpages.tar.gz ]; then
    mkdir -p $WORKSPACE/docs/target/{asciidoc,manpages}
    cp -fR $WORKSPACE/docs/content/manpages/* $WORKSPACE/docs/target/asciidoc
    find $WORKSPACE/docs/target/asciidoc/ -name "*.txt" -exec sed -i "s|//:||" {} \;
    find $WORKSPACE/docs/target/asciidoc/ -name "*.txt" -exec a2x -d manpage -f manpage {} -D $WORKSPACE/docs/target/manpages \;
    tar -czf $WORKSPACE/docs/target/manpages.tar.gz -C $WORKSPACE/docs/target/manpages/ .
fi
## Copy over the puppet scripts
if [ ! -f $WORKSPACE/deploy/target/puppet-scripts.tar.gz ]; then
    tar -czf $WORKSPACE/deploy/target/puppet-scripts.tar.gz -C $WORKSPACE/deploy/packaging/puppet geowave
fi
