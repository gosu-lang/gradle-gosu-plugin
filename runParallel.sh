#!/bin/bash

echo "Total No.of nodes... ${CIRCLE_NODE_TOTAL}"
echo "Current node index....${CIRCLE_NODE_INDEX}"

testFiles=$(find src/test/groovy/org/gosulang/gradle -name '*.groovy' |
sort | awk "NR % ${CIRCLE_NODE_TOTAL} == ${CIRCLE_NODE_INDEX}"|awk -F "/groovy/" '{print $2}'|awk -F "." '{print "--tests "$1}'|tr '/' '.')
echo $testFiles
./gradlew test $testFiles
