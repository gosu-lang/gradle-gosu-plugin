#!/bin/bash
# detects presence of -SNAPSHOT keyword in POM's <version> tag
#if xpath -q -e project -e version $TRAVIS_BUILD_DIR/gosu-parent/pom.xml | grep -q -e -SNAPSHOT\</version\>$; then
# detects presence of -SNAPSHOT keyword in gradle.properties version key/value pair
if grep -q -e "^version\s*=\s*.*-SNAPSHOT\s*$" $TRAVIS_BUILD_DIR/gradle.properties; then
	export RELEASE=false
	echo This is a snapshot.
else
	export RELEASE=true
	echo This is a release.
fi
