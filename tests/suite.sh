#!/usr/bin/env bash

cd $(dirname $0)

ls *.{css,js} | egrep -v '\.min$' | while read testfile; do
	# Get the jar to use.
	jar="$(ls ../build/*.jar | sort | tail -n1)"
	
	expected="$(
		cat $( ls $testfile* | egrep '\.min$' )
	)"
	actual="$(
		java -jar $jar $testfile
	)"

	if [ "$expected" == "$actual" ]; then
		echo "Passed: $testfile" > /dev/stderr
	else
		(
			echo "Test failed: $testfile"
			echo ""
			echo "Expected:"
			echo "$expected"
			echo ""
			echo "Actual:"
			echo "$actual"
		) > /dev/stderr
		exit 1
	fi
done

exit 0