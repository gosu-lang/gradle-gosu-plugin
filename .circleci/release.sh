#!/usr/bin/env bash

echo "Release Gosu job"
git config user.email "gosu.lang.team@gmail.com"
git config user.name "circleCi-bot"
git remote add upstream origin ${CIRCLE_BRANCH}
./gradlew release -Pgradle.publish.key=$gradlePublishKey -Pgradle.publish.secret=$gradlePublishSecret