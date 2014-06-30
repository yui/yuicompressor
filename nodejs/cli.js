#!/usr/bin/env node

/*
Just a simple nodejs wrapper around the .jar file
for easy CLI use
*/

var kexec = require('kexec'),
    fs = require('fs'),
    compressor = require('./index'),
    args = process.argv.slice(2);

args.unshift(compressor.jar);
args.unshift('-jar');
args.unshift('java');
kexec(args.join(' '));
