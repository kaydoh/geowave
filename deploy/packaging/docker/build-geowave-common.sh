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
mvn -q -o -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive -f $WORKSPACE/pom.xml exec:exec | sed -e 's/"//g' -e 's/-SNAPSHOT//g' > $WORKSPACE/deploy/target/version.txt

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
