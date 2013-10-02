#!/bin/bash

. setup.sh

for m in "${!MOD2PKG[@]}"; do
    versionCode=$(xml sel -t -v "//manifest/@android:versionCode" ${BASEDIR}/${m}/AndroidManifest.xml)
    moduleName=${m#module-}
    cat <<EOF > ${FDROIDMETA}/${MOD2PKG[${m}]}.txt
Category:System
License:GPLv3
Web Site:http://projectmaxs.org
Source Code:https://bitbucket.org/projectmaxs/maxs/
Bitcoin:1AUuXzvVUh1HMb2kVYnDWz8TgjbJMaZqDt

Auto Name:MAXS Module ${moduleName}
Summary:A Module for MAXS
Description:
This is a Module for MAXS, which does not install any launcher.
You need "MAXS Main" and a configured MAXS Transport to make use of it.
.

Repo Type:git
Repo:https://bitbucket.org/projectmaxs/maxs.git

Auto Update Mode:None
Update Check Mode:Tags
Current Version:0.0.1.0
Current Version Code:1

Build Version:0.0.1.0,1,0.0.1.0,\\
init=(cd .. && make ${m}/Makefile),\\
subdir=${m},\\
prebuild=make shared
EOF
done
