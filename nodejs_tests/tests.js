var YUITest = require('yuitest'),
    Assert = YUITest.Assert,
    suite = new YUITest.TestSuite('YUICompressor Tests'),
    path = require('path'),
    fs = require('fs'),
    compressor = require('../nodejs/index'),
    exists = fs.existsSync || path.existsSync;


var base = path.join(__dirname, '../tests');
var files = fs.readdirSync(base);
var testFiles = [];

files.forEach(function(file) {
    var ext = path.extname(file);
    if (ext === '.js' || ext === '.css') {
        var comp = path.join(base, file + '.min');
        if (exists(comp)) {
            testFiles.push({
                type: ext.replace('.', ''),
                test: file,
                result: (fs.readFileSync(path.join(base, file + '.min'), 'utf8')).trim()
            });
        }
    }
});

testFiles.forEach(function(item) {
    suite.add(new YUITest.TestCase({
        name: item.test,
        'test: compress': function() {
            var test = this;
            compressor.compress(path.join(base, item.test), {
                type: item.type,
                charset: 'utf8'
            }, function(err, out) {
                test.resume(function() {
                    Assert.isNull(err, 'error object should be null');
                    Assert.areEqual(out, item.result, 'Failed to properly compress');
                });
            });
            test.wait();
        }
    }));

});

suite.add(new YUITest.TestCase({
    name: 'Others',
    'test: error no file': function() {
        var test = this;
        compressor.compress('/path/to/no/file', function(err, data) {
            test.resume(function() {
                Assert.areEqual(data, '', 'should not return data');
                Assert.isTrue(err.indexOf('[ERROR]') > -1, 'should have [ERROR] in string');
            });
        });
        test.wait();
    },
    'test: string to compress': function() {
        var test = this,
            given = 'var x = (function() { var foo = 1,  bar = 2; return (foo + bar) }())',
            expected = 'var x=(function(){var b=1,a=2;return(b+a)}());';
        compressor.compress(given, function(err, data) {
            test.resume(function() {
                Assert.isNull(err, 'error object should be null');
                Assert.areEqual(data, expected, 'failed to compress string');
            });
        });
        test.wait();
    }
}));

var expectedYUI = fs.readFileSync(path.join(__dirname, 'files', 'yui.js.min'), 'utf8');

suite.add(new YUITest.TestCase({
    name: 'Large file support',
    'test compress yui.js as file': function() {
        var test = this;
        compressor.compress(path.join(__dirname, 'files', 'yui.js'), function(err, data) {
            test.resume(function() {
                Assert.isNull(err, 'error object should be null');
                Assert.areEqual(data, expectedYUI, 'failed to minify a large file');
            });
        });
        test.wait();
    },
    'test compress yui.js as string': function() {
        var test = this,
            given = fs.readFileSync(path.join(__dirname, 'files', 'yui.js'), 'utf8');
            
        compressor.compress(given, function(err, data) {
            test.resume(function() {
                Assert.isNull(err, 'error object should be null');
                Assert.areEqual(data, expectedYUI, 'failed to minify a large file');
            });
        });
        test.wait();
    }
}));

YUITest.TestRunner.add(suite);
