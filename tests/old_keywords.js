/**
 * tests a minifier bug in with outdated keywords as identifiers
 */
var is = {
    boolean: function(v){
        return 'boolean' == typeof v;
    },
    int: function(v){
        return 'number' == typeof v;
    },
    byte: function(v){
        return 'number' == typeof v;
    },
    short: function(v){
        return 'number' == typeof v;
    },
    long: function(v){
        return 'number' == typeof v;
    },
    double: function(v){
        return 'number' == typeof v;
    },
    char: function(v){
        return 'number' == typeof v;
    }
};
