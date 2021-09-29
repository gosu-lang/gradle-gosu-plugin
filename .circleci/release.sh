#!/usr/bin/env bash

echo "Release Gosu job"
git config user.email "gosu.lang.team@gmail.com"
git config user.name "circleCi-bot"
git remote add upstream origin ${CIRCLE_BRANCH}
#echo "just an dummy line" >> gradle.properties
./gradlew release -Pgradle.publish.key=$gradlePublishKey -Pgradle.publish.secret=$gradlePublishSecret
#git add -u
#git commit -m "Checking test commit works fine from CI to github"
#git push --set-upstream origin ${CIRCLE_BRANCH}