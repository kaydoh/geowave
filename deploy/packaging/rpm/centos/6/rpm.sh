#!/bin/bash
#
# RPM build script
#

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Source all our reusable functionality, argument is the location of this script.
. "$SCRIPT_DIR/../../rpm-functions.sh" "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

declare -A ARGS
while [ $# -gt 0 ]; do
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done

GEOWAVE_VERSION=${ARGS[geowave-version]}

case ${ARGS[command]} in
    build-vendor) rpmbuild \
                --define "_topdir $(pwd)" \
                --define "_version $GEOWAVE_VERSION" \
                --define "_vendor_version ${ARGS[vendor-version]}" \
                --define "_priority $(parsePriorityFromVersion $GEOWAVE_VERSION)" \
                $(buildArg "${ARGS[buildarg]}") SPECS/*-vendor.spec ;;
                
    build-common) rpmbuild \
                --define "_topdir $(pwd)" \
                --define "_version $GEOWAVE_VERSION" \
                --define "_priority $(parsePriorityFromVersion $GEOWAVE_VERSION)" \
                $(buildArg "${ARGS[buildarg]}") SPECS/*-common.spec ;;
    clean) clean ;;
esac
