YUI Compressor
=============

The YUI Compressor is written in Java (requires Java >= 1.4) and relies on Rhino to tokenize the source JavaScript file. 
It starts by analyzing the source JavaScript file to understand how it is structured. It then prints out the token stream, 
omitting as many white space characters as possible, and replacing all local symbols by a 1 (or 2, or 3) letter symbol 
wherever such a substitution is appropriate (in the face of evil features such as eval or with, the YUI Compressor takes
a defensive approach by not obfuscating any of the scopes containing the evil statement) The CSS compression algorithm 
uses a set of finely tuned regular expressions to compress the source CSS file. The YUI Compressor is open-source, 
so don't hesitate to look at the code to understand exactly how it works.

Building
--------

    ant

Testing
-------

    ./tests/suite.sh

TODO
----

* Proper Unit Tests
* Proper Node.js Module
   * Wrapper for use in a Node.js process
* Better Docs
* Help Pages

Build Status
------------

[![Build Status](https://secure.travis-ci.org/yui/yuicompressor.png?branch=master)](http://travis-ci.org/yui/yuicompressor)
