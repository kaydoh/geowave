#!/bin/bash

# If we've not specifically disabled and there is no current Maven repo
# pull a cache from S3 so the first run won't take forever
if [ -z $NO_MAVEN_INIT ] && [ ! -d $1/.m2 ]; then
	echo "Downloading Maven Cache ..."
	MVN_CACHE_BASE=https://s3.amazonaws.com/geowave-deploy/cache-bundle
	CACHE_FILE=mvn-repo-cache-20161129.tar
	pushd $1
	curl -O $MVN_CACHE_BASE/$CACHE_FILE
	tar xf $CACHE_FILE
	rm -f $CACHE_FILE
	popd
	type getenforce >/dev/null 2>&1 &&  getenforce >/dev/null 2>&1 && chcon -Rt svirt_sandbox_file_t ~/.m2;
	echo "Finished Downloading Maven Cache ..."
fi


if [ -z $GEOSERVER_DOWNLOAD_BASE ]; then
	GEOSERVER_DOWNLOAD_BASE=https://s3.amazonaws.com/geowave-deploy/third-party-downloads/geoserver
fi

if [ -z $GEOSERVER_VERSION ]; then
	GEOSERVER_VERSION=geoserver-2.10.0-bin.zip
fi

GEOSERVER_ARTIFACT=/usr/src/geowave/deploy/packaging/rpm/centos/6/SOURCES/geoserver.zip
if [ ! -f "$GEOSERVER_ARTIFACT" ]; then
	curl $GEOSERVER_DOWNLOAD_BASE/$GEOSERVER_VERSION > $GEOSERVER_ARTIFACT
fi