/**
 * cssmin.js
 * Author: Stoyan Stefanov - http://phpied.com/
 * This is a JavaScript port of the CSS minification tool
 * distributed with YUICompressor, itself a port 
 * of the cssmin utility by Isaac Schlueter - http://foohack.com/ 
 * Permission is hereby granted to use the JavaScript version under the same
 * conditions as the YUICompressor (original YUICompressor note below).
 */
 
/*
* YUI Compressor
* Author: Julien Lecomte - http://www.julienlecomte.net/
* Copyright (c) 2009 Yahoo! Inc. All rights reserved.
* The copyrights embodied in the content of this file are licensed
* by Yahoo! Inc. under the BSD (revised) open source license.
*/
var YAHOO = YAHOO || {};
YAHOO.compressor = YAHOO.compressor || {};
YAHOO.compressor.cssmin = function (css, linebreakpos){

    var startIndex = 0, 
        endIndex = 0,
        iemac = false,
        preserve = false,
        i = 0, max = 0,
        preservedTokens = [],
        token = '';

    // preserve strings so their content doesn't get accidentally minified
    css = css.replace(/("([^\\"]|\\.|\\)*")|('([^\\']|\\.|\\)*')/g, function(match) {
        var quote = match[0];
        preservedTokens.push(match.slice(1, -1));
        return quote + "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.length - 1) + "___" + quote;
    });

    // Remove all comment blocks...
    while ((startIndex = css.indexOf("/*", startIndex)) >= 0) {
        preserve = css.length > startIndex + 2 && css[startIndex + 2] === '!';
        endIndex = css.indexOf("*/", startIndex + 2);
        if (endIndex < 0) {
            if (!preserve) {
                css = css.slice(0, startIndex);
            }
        } else if (endIndex >= startIndex + 2) {
            if (css[endIndex - 1] === '\\') {
                // Looks like a comment to hide rules from IE Mac.
                // Leave this comment, and the following one, but shorten them
                css = css.slice(0, startIndex) + "/*\\*/" + css.slice(endIndex + 2);
                startIndex += 5;
                iemac = true;
            } else if (iemac && !preserve) {
                css = css.slice(0, startIndex) + "/**/" + css.slice(endIndex + 2);
                startIndex += 4;
                iemac = false;
            } else if (!preserve) {
                css = css.slice(0, startIndex) + css.slice(endIndex + 2);
            } else {
                // preserve
                token = css.slice(startIndex+3, endIndex); // 3 is "/*!".length
                preservedTokens.push(token);
                css = css.slice(0, startIndex+2) + "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.length - 1) + "___" + css.slice(endIndex);
                if (iemac) iemac = false;
                startIndex += 2;
            }
        }
    }
    
    // Normalize all whitespace strings to single spaces. Easier to work with that way.
    css = css.replace(/\s+/g, " ");

    // Remove the spaces before the things that should not have spaces before them.
    // But, be careful not to turn "p :link {...}" into "p:link{...}"
    // Swap out any pseudo-class colons with the token, and then swap back.
    css = css.replace(/(^|\})(([^\{:])+:)+([^\{]*\{)/g, function(m) {
        return m.replace(":", "___YUICSSMIN_PSEUDOCLASSCOLON___");
    });
    css = css.replace(/\s+([!{};:>+\(\)\],])/g, '$1');
    css = css.replace(/___YUICSSMIN_PSEUDOCLASSCOLON___/g, ":");

    // retain space for special IE6 cases
    css = css.replace(/:first-(line|letter)({|,)/g, ":first-$1 $2");
        
    // no space after the end of a preserved comment
    css = css.replace(/\*\/ /g, '*/'); 
    
     
    // If there is a @charset, then only allow one, and push to the top of the file.
    css = css.replace(/^(.*)(@charset "[^"]*";)/gi, '$2$1');
    css = css.replace(/^(\s*@charset [^;]+;\s*)+/gi, '$1');
    
    // Put the space back in some cases, to support stuff like
    // @media screen and (-webkit-min-device-pixel-ratio:0){
    css = css.replace(/\band\(/gi, "and (");
    

    // Remove the spaces after the things that should not have spaces after them.
    css = css.replace(/([!{}:;>+\(\[,])\s+/g, '$1');

    // remove unnecessary semicolons
    css = css.replace(/;+}/g, "}");

    // Replace 0(px,em,%) with 0.
    css = css.replace(/([\s:])(0)(px|em|%|in|cm|mm|pc|pt|ex)/gi, "$1$2");

    // Replace 0 0 0 0; with 0.
    css = css.replace(/:0 0 0 0;/g, ":0;");
    css = css.replace(/:0 0 0;/g, ":0;");
    css = css.replace(/:0 0;/g, ":0;");
    // Replace background-position:0; with background-position:0 0;
    css = css.replace(/background-position:0;/gi, "background-position:0 0;");

    // Replace 0.6 to .6, but only when preceded by : or a white-space
    css = css.replace(/(:|\s)0+\.(\d+)/g, "$1.$2");

    // Shorten colors from rgb(51,102,153) to #336699
    // This makes it more likely that it'll get further compressed in the next step.
    css = css.replace(/rgb\s*\(\s*([0-9,\s]+)\s*\)/gi, function(){
        var rgbcolors = arguments[1].split(',');
        for (var i = 0; i < rgbcolors.length; i++) {
            rgbcolors[i] = parseInt(rgbcolors[i], 10).toString(16);
            if (rgbcolors[i].length === 1) {
                rgbcolors[i] = '0' + rgbcolors[i];
            }
        }
        return '#' + rgbcolors.join('');
    });
    

    // Shorten colors from #AABBCC to #ABC. Note that we want to make sure
    // the color is not preceded by either ", " or =. Indeed, the property
    //     filter: chroma(color="#FFFFFF");
    // would become
    //     filter: chroma(color="#FFF");
    // which makes the filter break in IE.
    css = css.replace(/([^"'=\s])(\s*)#([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])/gi, function(){ 
        var group = arguments;
        if (
            group[3].toLowerCase() === group[4].toLowerCase() &&
            group[5].toLowerCase() === group[6].toLowerCase() &&
            group[7].toLowerCase() === group[8].toLowerCase()
        ) {
            return (group[1] + group[2] + '#' + group[3] + group[5] + group[7]).toLowerCase();
        } else {
            return group[0].toLowerCase();
        }
    });
    

    // Remove empty rules.
    css = css.replace(/[^\};\{\/]+\{\}/g, "");

    if (linebreakpos >= 0) {
        // Some source control tools don't like it when files containing lines longer
        // than, say 8000 characters, are checked in. The linebreak option is used in
        // that case to split long lines after a specific column.
        startIndex = 0; 
        i = 0;
        while (i < css.length) {
            if (css[i++] === '}' && i - startIndex > linebreakpos) {
                css = css.slice(0, i) + '\n' + css.slice(i);
                startIndex = i;
            }
        }
    }

    // Replace multiple semi-colons in a row by a single one
    // See SF bug #1980989
    css = css.replace(/;;+/g, ";");

    // restore preserved comments and strings
    for(i = 0, max = preservedTokens.length; i < max; i++) {
        css = css.replace("___YUICSSMIN_PRESERVED_TOKEN_" + i + "___", preservedTokens[i]);
    }
    
    // Trim the final string (for any leading or trailing white spaces)
    css = css.replace(/^\s+|\s+$/g, "");

    return css;

};