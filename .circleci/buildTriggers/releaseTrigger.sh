#!/usr/bin/env bash

if [ "$1" == "-h" ]; then
  echo "Usage: `basename $0`  branchName"
  echo "	For ex: `basename $0` rel/6.55.1"
  exit 0
fi

branchName=$(echo $1 | xargs)

echo ""
echo "branchName passed: $branchName"

echo "......Validating version number"

current_version=`cat ../../gradle.properties|grep '^version='`
echo "current_version....$current_version"
if [[ "$current_version" != *SNAPSHOT ]] ; then
   echo "Artifact version : $current_version should end with snapshot before the release"
   echo exit 1
   else
     echo ""
     echo ".....Triggering release workflow in circleCI"
     echo ""
fi

curl  --request POST \
	--url https://circleci.com/api/v2/project/gh/gosu-lang/gradle-gosu-plugin/pipeline \
	--header "Circle-Token: ${CIRCLECI_API_TOKEN}" \
	--header 'content-type: application/json' \
	--data @/dev/stdin<<EOF
		{"parameters":{"run_workflow_release":true}, "branch" : "$branchName"}
		EOF
