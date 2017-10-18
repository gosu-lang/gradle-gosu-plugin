#!/bin/bash

sed -i '/testedVersions =/d' ./gradle.properties
str=`grep 'testedVersionsCI' gradle.properties|awk -F "=" '{print $2}'`

arr=(`echo $str|sed 's/, /\n/g'`)

size=${#arr[@]}

let nt=${CIRCLE_NODE_TOTAL}
let ni=${CIRCLE_NODE_INDEX}

if [ `expr $size % $nt` -eq 0 ] ; then
   count=$(( $size / $nt ))
else
   count=$(( ($size/$nt)+1 ))
fi

echo "count....$count"

if [ $ni -eq 0 ] ; then
        new=("${arr[@]:0:$count}")
fi
if [ $ni -eq $((nt-1)) ] ; then
        new=("${arr[@]: $(($ni * $count)) : $(($size-1))}")
else
        new=("${arr[@]: $(($count * $ni)) :$count }")
fi

ver=$(IFS=','; echo "${new[*]}")
echo "Gradle versions on this container are...$ver"

echo "testedVersions=$ver">>gradle.properties

./gradlew check --no-daemon
