#!/bin/bash

project_dir="$1"
MAX_DEPTH="$2"

generate_files() {
	base_dir=$(mktemp -p . -d)
	pushd $base_dir
	for i in {1..1000}; do
		echo 1 > $i
	done
	for i in {1..100}; do
		mktemp -p . -d
	done
	depth=$((depth+1))
	if [ $depth -lt $MAX_DEPTH ]; then
		generate_files
	fi
	popd
}

pushd $project_dir

depth=0
generate_files

popd
