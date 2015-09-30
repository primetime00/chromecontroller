#!/bin/sh

for file in ./*proto; do
	if [ -e $file ]; then
		protoc -I=./ $file --cpp_out=../linux/protobuf/
	fi
done

