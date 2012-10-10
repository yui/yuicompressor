YUI Compressor Docs
===================


These docs are what powers http://yui.github.com/yuicompressor

They are powered by [Selleck](http://yui.github.com/selleck).

```terminal
$ npm -g install selleck
```

How to build them
-----------------

```terminal
$ git clone git://github.com/yui/yuicompressor.git
$ git clone git://github.com/yui/yuicompressor.git yuicompressor-pages

cd yuicompressor-pages
git fetch
git checkout -t gh-pages
```

This should pull the `gh-pages` branch locally.

```terminal
cd ../yuicompressor/docs/
selleck -o ../../yuicompressor-pages/
```

Done!
