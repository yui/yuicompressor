/*
 * cssmin.common.js
 * cssmin as a CommonJS module (to be used with nodejs for example)
 * Author: Louis-Rémi Babé - http://twitter.com/louis_remi
 * 
 * unit-tests available for your Web browser: https://github.com/lrbabe/html-minifier/blob/gh-pages/tests/css.html
 * 
 * YUI Compressor
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Author: Isaac Schlueter - http://foohack.com/
 * Author: Stoyan Stefanov - http://phpied.com/
 * Copyright (c) 2009 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */

(function(global){  

function cssmin(css) {
  var comments = [],
      commentsLen = 0,
      tokens = [],
      tokensLen = 0,
      charset = '',
      newLine = '\n',
      nextComment;
      
  // Concatenate multiple files
  if(css instanceof Array) {
    css = css.join(' ');
  }
  
  if (css.indexOf('\n\r') != -1) {
    newLine = '\n\r';
  }
  else if (css.indexOf('\r\n') != -1) {
    newLine = '\r\n';
  }
  
  // remove all newline temporarily
  css = css.split(newLine).join('\uffff');
  
  // Collect all comment blocks
  // keep empty comments after child selectors (IE7 hack)
  // e.g. html >/**/ body
  css = css.replace(/(^|.|\uffff)\/\*(.*?)\*\//g, function(str, previousChar, content) {
    comments.push(content);
    return previousChar == '>' && content == ''?
      str:
      previousChar + "___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" + (commentsLen++) + "___";
  });
  
  // Preserve strings so their content doesn't get accidentally minified
  css = css.replace(/(["'])(.*?[^\\])\1/g, function(str, quote, content) {
    // maybe the string contains comment-like substring?
    content = content.replace(/___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_(\d+)___/g, function(str, i) {
      return '/*'+comments[+i]+'*/';
    });
    
    // minify alpha opacity in filter strings
    tokens.push(minifyOpacity(quote+content+quote));
    return "___YUICSSMIN_PRESERVED_TOKEN_" + (tokensLen++) + "___";
  });
  
  // strings are safe, now wrestle the comments
  css = css.replace(/___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_(\d+)___/g, function(str, i) {
    var comment = comments[+i];
    
    if (nextComment) {
      nextComment = false;
      return '/**/';
    }
    // ! in the first position of the comment means preserve
    // so push to the preserved tokens while keeping the !
    else if (comment[0] == '!') {
      tokens.push(comment);
      return "/*___YUICSSMIN_PRESERVED_TOKEN_" + (tokensLen++) + "___*/";
    }
    
    // \ in the last position looks like hack for Mac/IE5
    // shorten that to /*\*/ and the next one to /**/
    else if (comment.substr(-1) == '\\') {
      nextComment = true;
      return '/*\\*/';
    }
    
    // in all other cases kill the comment
    return '';
  });
  
  // Normalize all whitespace strings to single spaces. Easier to work with that way.
  css = css.split('\uffff').join(' ');
  css = css.replace(/\s+/g, " ");
  
  // Remove the spaces before the things that should not have spaces before them.
  // But, be careful not to turn "p :link {...}" into "p:link{...}"
  // Swap out any pseudo-class colons with the token, and then swap back.
  css = css.replace(/((?:^|}|\uffff)[^{]*?):([^}]*?(?:{|$))/, '$1___YUICSSMIN_PSEUDOCLASSCOLON___$2');
  
  // Remove spaces before the things that should not have spaces before them.
  css = css.replace(/ ([!{};:>+\(\)\],])/g, '$1');
  // bring back the colon
  css = css.split("___YUICSSMIN_PSEUDOCLASSCOLON___").join(':');
  
  // retain space for special IE6 cases
  css = css.replace(/:first-(line|letter)(?={|,)/g, ":first-$1 ");
  
  // If there is a @charset, then only allow one, and push to the top of the file.
  css = css.replace(/@charset .*?;/g, function(str) {
    if (!charset) {
      charset = str;
    }
    return '';
  });
  css = charset + css;
  
  // Put the space back in some cases, to support stuff like
  // @media screen and (-webkit-min-device-pixel-ratio:0){
  css = css.replace(/\band\(/g, "and (");

  // Remove the spaces after the things that should not have spaces after them.
  css = css.replace(/([!}{:;>+\([,]) +/g, '$1');

  // remove unnecessary semicolons
  css = css.replace(/;+}/g, '}');

  // Replace 0(px,em,%) with 0.
  css = css.replace(/([ :])(0)(px|em|%|in|cm|mm|pc|pt|ex)/g, "$10");

  // Replace 0 0 0 0; with 0.
  css = css.replace(/:0(?: 0){1,3}(?=;|})/g, ":0");
  // Replace background-position:0; with background-position:0 0;
  css = css.replace(/background-position:0(?=;|})/gi, "background-position:0 0");

  // Replace 0.6 to .6, but only when preceded by : or a white-space
  css = css.replace(/([: ])0+(?=\.\d+)/g, "$1");
  
  // Shorten colors from rgb(51,102,153) to #336699
  // This makes it more likely that it'll get further compressed in the next step.
  css = css.replace(/rgb\((\d+),(\d+),(\d+)\)/g, function(str, r, g, b) {
    return '#'+toHex(r)+toHex(g)+toHex(b);
  });
  
  // Shorten colors from #AABBCC to #ABC. Note that we want to make sure
  // the color is not preceded by either ", " or =. Indeed, the property
  // filter: chroma(color="#FFFFFF");
  // would become
  // filter: chroma(color="#FFF");
  // which makes the filter break in IE.
  css = css.replace(/([^"'=] ?)#([0-9a-f])\2([0-9a-f])\3([0-9a-f])\4/gi, "$1#$2$3$4");
  
  // shorter opacity IE filter
  css = minifyOpacity(css);
  
  // Remove empty rules.
  css = css.replace(/[^}{\/;]+\{\}/g, '');
  
  // Replace multiple semi-colons in a row by a single one
  // See SF bug #1980989
  css = css.replace(/;+ */g, ';');
  
  // restore preserved comments and strings
  css = css.replace(/___YUICSSMIN_PRESERVED_TOKEN_(\d+)___/g, function(str, i) {
    return tokens[+i].split('\uffff').join('\n');
  });
  
  // no space after the end of a preserved comment
  css = css.split("*/ ").join("*/");

  // Trim the final string (for any leading or trailing white spaces)
  return css.replace(/^\s\s*/, '').replace(/\s\s*$/, '');  
  
  function minifyOpacity(str) {
    return str.replace(/progid:DXImageTransform.Microsoft.Alpha\(Opacity=/gi, "alpha(opacity=");
  }
  
  function toHex(color) {
    color = +color;
    return (color < 16? '0' : '') + color.toString(16);
  }
}

// export
global.cssmin = cssmin;

})(this);