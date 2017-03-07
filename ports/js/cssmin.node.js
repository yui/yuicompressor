/**
 * node.js
 * Author: Corin Lawson - https://github.com/au-phiware
 * This is a wrapper of cssmin.js, suitable for use in Node.js.
 * Permission is hereby granted to do what you you see fit.
 */
(function() {
    eval(String(require('fs').readFileSync(__dirname + '/cssmin.js')));
    exports.compressor = YAHOO.compressor;
    exports.cssmin = function () { return YAHOO.compressor.cssmin.apply(YAHOO.compressor, arguments); };
})();
