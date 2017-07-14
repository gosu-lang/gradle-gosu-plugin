#!/bin/bash

echo $0
sed -i '/testedVersions =/d' ./gradle.properties
str=`grep 'testedVersionsCI' gradle.properties|awk -F "=" '{print $2}'`

arr=(`echo $str|sed 's/, /\n/g'`)

echo "Array...${arr[@]}"
size=${#arr[@]}

let nt=${CIRCLE_NODE_TOTAL}
let ni=${CIRCLE_NODE_INDEX}

if [ `expr $size % $nt` -eq 0 ] ; then
   index=$(( $size / $nt ))
else
   index=$(( ($size/$nt)+1 ))
fi

echo "index....$index"

if [ $ni -eq 0 ] ; then
        new=("${arr[@]:0:$index}")
fi
if [ $ni -eq $((nt-1)) ] ; then
        new=("${arr[@]: $(($ni * $index)) : $(($size-1))}")
else
        new=("${arr[@]: $(($index * $ni)) :$index }")
fi

var=$(IFS=','; echo "${new[*]}")

echo "testedVersions=$var">>gradle.properties

./gradlew test