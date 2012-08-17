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
                    Assert.areSame(out, item.result, 'Failed to properly compress');
                });
            });
            test.wait();
        }
    }));

});

YUITest.TestRunner.add(suite);
