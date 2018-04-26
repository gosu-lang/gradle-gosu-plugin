#!/bin/bash
echo "Wow let's do a release!"
git config --local user.name "Gosu CI"
git config --local user.email "gosu.lang.team@gmail.com"
git remote add origin-release https://${GH_TOKEN}@github.com/gosu-lang/gradle-gosu-plugin.git > /dev/null 2>&1
./gradlew release -Prelease.useAutomaticVersion=true -Prelease.git.pushToRemote=origin-release
