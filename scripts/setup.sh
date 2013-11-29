#!/bin/bash
set -e

while getopts d OPTION "$@"; do
    case $OPTION in
	d)
	    set -x
	    ;;
    esac
done

# Pretty fancy method to get reliable the absolute path of a shell
# script, *even if it is sourced*. Credits go to GreenFox on
# stackoverflow: http://stackoverflow.com/a/12197518/194894
pushd . > /dev/null
SCRIPTDIR="${BASH_SOURCE[0]}";
while([ -h "${SCRIPTDIR}" ]); do
    cd "`dirname "${SCRIPTDIR}"`"
    SCRIPTDIR="$(readlink "`basename "${SCRIPTDIR}"`")";
done
cd "`dirname "${SCRIPTDIR}"`" > /dev/null
SCRIPTDIR="`pwd`";
popd  > /dev/null

BASEDIR="$(cd ${SCRIPTDIR}/.. && pwd)"
HOMEPAGE="${BASEDIR}/homepage"
DOCDIR="${BASEDIR}/documentation"
MAINDIR="${BASEDIR}/main"
TRANSPORTS="$(find $BASEDIR -mindepth 1 -maxdepth 1 -type d -name 'transport-*')"
MODULES="$(find $BASEDIR -mindepth 1 -maxdepth 1 -type d -name 'module-*')"
COMPONENTS="${MAINDIR} ${TRANSPORTS} ${MODULES}"

if command -v xml &> /dev/null; then
    declare -A MOD2PKG
    for m in $MODULES ; do
	module_name=$(basename $m)
	module_package=$(xml sel -t -v "//manifest/@package" ${m}/AndroidManifest.xml)
	MOD2PKG[${module_name}]=${module_package}
    done
fi

if [[ -f ${BASEDIR}/config ]]; then
    # config is there, source it
    . ${BASEDIR}/config
    # and set further env variables based on the config
    FDROIDMETA="${FDROIDDATA}/metadata"
fi
