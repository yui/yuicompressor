#!/bin/sh
java -jar $(/bin/ls $(dirname `readlink -f $0`)/yuicompressor-*.jar | sort -V | tail -1) $@;
