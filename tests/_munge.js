(function() {
    var w = window;

    // logger
    //
    // var c = 1..toString();

    w.hello = function(a, abc) {
        // "a:nomunge"; // this useless statement causes a munging error
        var xyz = "a:nomunge",
            d = abc; // but it works fine like this



        w.alert("Hello, " + a);
    };
})();
