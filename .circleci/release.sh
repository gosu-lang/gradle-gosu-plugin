#!/usr/bin/env bash

echo "Release Gosu job"
./gradlew release -Pgradle.publish.key=$gradlePublishKey -Pgradle.publish.secret=$gradlePublishSecret