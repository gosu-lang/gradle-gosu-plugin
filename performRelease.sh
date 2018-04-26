#!/bin/bash
echo "Wow let's do a release!"
git config --local user.name "Gosu CI"
git config --local user.email "gosu.lang.team@gmail.com"
git commit --allow-empty -m 'Empty commit to test git on Travis'
git show HEAD
git push -u origin $TRAVIS_BRANCH
