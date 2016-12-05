# Build and archive HTML/PDF docs
if [ ! -f $WORKSPACE/target/site.tar.gz ]; then
    mvn javadoc:aggregate $BUILD_ARGS "$@"
    mvn -P docs -pl docs install $BUILD_ARGS "$@"
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

# Push our compiled docs to S3 if aws command has been installed
if command -v aws >/dev/null 2>&1 ; then
    aws s3 cp geowave/documentation.pdf s3://geowave/docs/
fi
