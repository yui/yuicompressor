<?php namespace Yahoo;

/**
 * cssmin.php
 * Author: Stéphane Goetz - http://onigoetz.ch/
 * This is a PHP port of the CSS minification tool
 * distributed with YUICompressor, itself a port
 * of the cssmin utility by Isaac Schlueter - http://foohack.com/
 * Permission is hereby granted to use the JavaScript version under the same
 * conditions as the YUICompressor (original YUICompressor note below).
 */

/*
* YUI Compressor
* http://developer.yahoo.com/yui/compressor/
* Author: Julien Lecomte - http://www.julienlecomte.net/
* Copyright (c) 2011 Yahoo! Inc. All rights reserved.
* The copyrights embodied in the content of this file are licensed
* by Yahoo! Inc. under the BSD (revised) open source license.
*/

class Compressor
{
    /**
     * Contains all comments
     *
     * @var array
     */
    public $comments;

    /**
     * Contains all string tokens that are conserved
     *
     * @var array
     */
    public $preservedTokens;

    /**
     * Utility method to replace all data urls with tokens before we start
     * compressing, to avoid performance issues running some of the subsequent
     * regexes against large strings chunks.
     *
     * @param string $css css The input css
     * @return string The processed css
     */
    private function _extractDataUrls($css)
    {

        // Leave data urls alone to increase parse performance.
        $maxIndex = strlen($css) - 1;
        $appendIndex = 0; //current offset
        $sb = array();

        $pattern = '/url\(\s*(["\']?)data:/i';

        //declared = $startIndex, $endIndex, $terminator, $foundTerminator, $m, $preserver, $token,

        // Since we need to account for non-base64 data urls, we need to handle
        // ' and ) being part of the data string. Hence switching to indexOf,
        // to determine whether or not we have matching string terminators and
        // handling sb appends directly, instead of using matcher.append* methods.

        while (preg_match($pattern, $css, $m, PREG_OFFSET_CAPTURE, $appendIndex)) {

            $terminator = $m[1][0]; // ', " or empty (not quoted)

            if (strlen($terminator) === 0) {
                $terminator = ")";
            }

            $foundTerminator = false;

            $lastIndex = $m[0][1] + strlen($m[0][0]);
            $endIndex = $lastIndex - 1;

            while ($foundTerminator === false && $endIndex + 1 <= $maxIndex) {
                $endIndex = strpos($css, $terminator, $endIndex + 1);

                // endIndex == 0 doesn't really apply here
                if (($endIndex > 0) && ($css[$endIndex - 1] !== '\\')) {
                    $foundTerminator = true;
                    if (")" != $terminator) {
                        $endIndex = strpos($css, ")", $endIndex);
                    }
                }
            }

            // Enough searching, start moving stuff over to the buffer
            $sb[] = substr($css, $appendIndex, $m[0][1] - $appendIndex);

            if ($foundTerminator) {
                $token = substr($css, $m[0][1] + 4, $endIndex - ($m[0][1] + 4));
                $token = preg_replace('/\s+/', "", $token);
                $this->preservedTokens[] = $token;
                $sb[] = "url(___YUICSSMIN_PRESERVED_TOKEN_" . (count($this->preservedTokens) - 1) . "___)";

                $appendIndex = $endIndex + 1;
            } else {
                echo 'no terminator';
                exit;
                // No end terminator found, re-add the whole match. Should we throw/warn here?
                $sb[] = substr($css, $m[0][1], $lastIndex - $m[0][1]);
                $appendIndex = $lastIndex;
            }
        }

        $sb[] = substr($css, $appendIndex);

        return join("", $sb);
    }

    /**
     * Utility method to compress hex color values of the form #AABBCC to #ABC.
     *
     * DOES NOT compress CSS ID selectors which match the above pattern (which would break things).
     * e.g. #AddressForm { ... }
     *
     * DOES NOT compress IE filters, which have hex color values (which would break things).
     * e.g. filter: chroma(color="#FFFFFF");
     *
     * DOES NOT compress invalid hex values.
     * e.g. background-color: #aabbccdd
     *
     * @param string $css The input css
     * @return string The processed css
     */
    private function _compressHexColors($css)
    {
        $appendIndex = 0;
        $sb = array();
        // Look for hex colors inside { ... } (to avoid IDs) and which don't have a =, or a " in front of them (to avoid filters)
        $pattern = '/(\=\s*?["\']?)?#([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])([0-9a-f])(\}|[^0-9a-f{][^{]*?\})/i';

        while (preg_match($pattern, $css, $m, PREG_OFFSET_CAPTURE, $appendIndex)) {
            $sb[] = substr($css, $appendIndex, $m[0][1] - $appendIndex);

            $isFilter = $m[1][0];

            if ($isFilter) {
                // Restore, maintain case, otherwise filter will break
                $sb[] = $m[1][0] . "#" . ($m[2][0] . $m[3][0] . $m[4][0] . $m[5][0] . $m[6][0] . $m[7][0]);
            } else {

                if (strtolower($m[2][0]) == strtolower($m[3][0]) &&
                    strtolower($m[4][0]) == strtolower($m[5][0]) &&
                    strtolower($m[6][0]) == strtolower($m[7][0])
                ) {

                    // Compress.
                    $sb[] = "#" . strtolower($m[3][0] . $m[5][0] . $m[7][0]);
                } else {
                    // Non compressible color, restore but lower case.
                    $sb[] = "#" . strtolower($m[2][0] . $m[3][0] . $m[4][0] . $m[5][0] . $m[6][0] . $m[7][0]);
                }
            }

            $appendIndex = $m[8][1];
        }

        $sb[] = substr($css, $appendIndex);

        return join("", $sb);
    }

    private function quote_replace_callback($match)
    {
        $quote = $match[0][0];
        $match = substr($match[0], 1, -1);

        // maybe the string contains a comment-like substring?
        // one, maybe more? put'em back then
        if (strpos($match, "___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_") !== false) {
            foreach ($this->comments as $i => $comment) {
                $match = str_replace("___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" . $i . "___", $comment, $match);
            }
        }

        // minify alpha opacity in filter strings
        $match = preg_replace('/progid:DXImageTransform\.Microsoft\.Alpha\(Opacity=/i', "alpha(opacity=", $match);

        $this->preservedTokens[] = $match;
        return $quote . "___YUICSSMIN_PRESERVED_TOKEN_" . (count($this->preservedTokens) - 1) . "___" . $quote;
    }

    private function comment_replace_callback($match)
    {
        $this->comments[] = $match[1];
        return "/*___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" . (count($this->comments) - 1) . "___*/";
    }

    public function cssmin($css, $linebreakpos)
    {

        $this->preservedTokens = array();
        $this->comments = array();

        $css = $this->_extractDataUrls($css, $this->preservedTokens);

        // collect all comment blocks...
        $css = preg_replace_callback("/\/\*(.*?)\*\//s", array($this, 'comment_replace_callback'), $css);

        // preserve strings so their content doesn't get accidentally minified
        $css = preg_replace_callback(
            '/"(?:[^"\\\\]|\\\\.)*"|\'(?:[^\'\\\\]|\\\\.)*\'/s',
            array($this, 'quote_replace_callback'),
            $css
        );

        // strings are safe, now wrestle the comments
        //for ($i = 0, $max = count($comments); $i < $max; $i = $i + 1) {
        foreach ($this->comments as $i => $token) {

            $placeholder = "___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" . $i . "___";
            $token_length = strlen($token);

            // ! in the first position of the comment means preserve
            // so push to the preserved tokens keeping the !
            if ($token_length > 0 && $token[0] === "!") {
                $this->preservedTokens[] = $token;
                $css = str_replace(
                    $placeholder,
                    "___YUICSSMIN_PRESERVED_TOKEN_" . (count($this->preservedTokens) - 1) . "___",
                    $css
                );
                continue;
            }

            // \ in the last position looks like hack for Mac/IE5
            // shorten that to /*\*/ and the next one to /**/
            if ($token_length > 0 && $token[strlen($token) - 1] === "\\") {
                $this->preservedTokens[] = "\\";
                $css = str_replace(
                    $placeholder,
                    "___YUICSSMIN_PRESERVED_TOKEN_" . (count($this->preservedTokens) - 1) . "___",
                    $css
                );
                $i = $i + 1; // attn: advancing the loop
                $this->preservedTokens[] = "";
                $css = str_replace(
                    "___YUICSSMIN_PRESERVE_CANDIDATE_COMMENT_" . $i . "___",
                    "___YUICSSMIN_PRESERVED_TOKEN_" . (count($this->preservedTokens) - 1) . "___",
                    $css
                );
                continue;
            }

            // keep empty comments after child selectors (IE7 hack)
            // e.g. html >/**/ body
            if ($token_length === 0) {
                $startIndex = strpos($css, $placeholder);
                if ($startIndex > 2) {
                    if ($css[$startIndex - 3] === '>') {
                        $this->preservedTokens[] = "";
                        $css = str_replace(
                            $placeholder,
                            "___YUICSSMIN_PRESERVED_TOKEN_" . (count($this->preservedTokens) - 1) . "___",
                            $css
                        );
                    }
                }
            }

            // in all other cases kill the comment
            $css = str_replace("/*" . $placeholder . "*/", "", $css);
        }

        // Normalize all whitespace strings to single spaces. Easier to work with that way.
        $css = preg_replace('/\s+/', " ", $css);

        // Remove the spaces before the things that should not have spaces before them.
        // But, be careful not to turn "p :link {...}" into "p:link{...}"
        // Swap out any pseudo-class colons with the token, and then swap back.
        $css = preg_replace_callback(
            '/(^|\})(([^\{:])+:)+([^\{]*\{)/',
            function ($m) {
                return str_replace(":", "___YUICSSMIN_PSEUDOCLASSCOLON___", $m[0]);
            },
            $css
        );
		// Remove spaces before the things that should not have spaces before them.
        $css = preg_replace('/\s+([!{};:>+\(\)\],])/', '$1', $css);
		// Restore spaces for !important
		$css = str_replace("!important", " !important", $css);			
		// bring back the colon
        $css = preg_replace('/___YUICSSMIN_PSEUDOCLASSCOLON___/', ":", $css);

        // retain space for special IE6 cases
        $css = preg_replace_callback(
            '/:first-(line|letter)(\{|,)/i',
            function ($m) {
                return ':first-' . strtolower($m[1]) . ' ' . $m[2];
            },
            $css
        );
		
        // no space after the end of a preserved comment
        $css = preg_replace('/\*\/ /', '*/', $css);

        // If there is a @charset, then only allow one, and push to the top of the file.
        $css = preg_replace('/^(.*)(@charset "[^"]*";)/i', '$2$1', $css);
        $css = preg_replace('/^(\s*@charset [^;]+;\s*)+/i', '$1', $css);
		
	    // If there is a @charset, then only allow one, and push to the top of the file (and make lowercase).
	    $css = preg_replace_callback(
			'/^(.*)(@charset)( "[^"]*";)/i',
			function ($m) {
	        	return strtolower($m[2]) . $m[3] . $m[1];
	    	},
			$css
		);
	    $css = preg_replace_callback(
			'/^((\s*)(@charset)( [^;]+;\s*))+/i',
			function ($m) {
	        	return $m[2] . strtolower($m[3]) . $m[4];
	    	},
			$css
		);
		
	    // lowercase some popular @directives (@charset is done right above)
        $css = preg_replace_callback(
            '/@(font-face|import|(?:-(?:atsc|khtml|moz|ms|o|wap|webkit)-)?keyframe|media|page|namespace)/i',
            function ($m) {
                return '@' . strtolower($m[1]);
            },
            $css
        );
 
	    // lowercase some more common pseudo-elements
        $css = preg_replace_callback(
            '/:(active|after|before|checked|disabled|empty|enabled|first-(?:child|of-type)|focus|hover|last-(?:child|of-type)|link|only-(?:child|of-type)|root|:selection|target|visited)/i',
            function ($m) {
                return ':' . strtolower($m[1]);
            },
            $css
        );
 
	    // lowercase some more common functions
        $css = preg_replace_callback(
            '/:(lang|not|nth-child|nth-last-child|nth-last-of-type|nth-of-type|(?:-(?:moz|webkit)-)?any)\(/i',
            function ($m) {
                return ':' . strtolower($m[1]) . '(';
            },
            $css
        );
 
	    // lower case some common function that can be values
	    // NOTE: rgb() isn't useful as we replace with #hex later, as well as and() is already done for us
        $css = preg_replace_callback(
            '/([:,\( ]\s*)(attr|color-stop|from|rgba|to|url|(?:-(?:atsc|khtml|moz|ms|o|wap|webkit)-)?(?:calc|max|min|(?:repeating-)?(?:linear|radial)-gradient)|-webkit-gradient)/i',
            function ($m) {
                return $m[1] . strtolower($m[2]);
            },
            $css
        );

        // Put the space back in some cases, to support stuff like
        // @media screen and (-webkit-min-device-pixel-ratio:0){
        $css = preg_replace('/\band\(/i', "and (", $css);

        // Remove the spaces after the things that should not have spaces after them.
        $css = preg_replace('/([!{}:;>+\(\[,])\s+/', '$1', $css);

        // remove unnecessary semicolons
        $css = preg_replace('/;+\}/', "}", $css);

        // Replace 0(px,em,%) with 0.
        $css = preg_replace('/(^|[^0-9])(?:0?\.)?0(?:px|em|%|in|cm|mm|pc|pt|ex|deg|g?rad|m?s|k?hz)/i', '${1}0', $css);

        // Replace 0 0 0 0; with 0.
        $css = preg_replace('/:0 0 0 0(;|\})/', ":0$1", $css);
        $css = preg_replace('/:0 0 0(;|\})/', ":0$1", $css);
        $css = preg_replace('/:0 0(;|\})/', ":0$1", $css);

        // Replace background-position:0; with background-position:0 0;
        // same for transform-origin
        $css = preg_replace_callback(
            '/(background-position|webkit-mask-position|transform-origin|webkit-transform-origin|moz-transform-origin|o-transform-origin|ms-transform-origin):0(;|\})/i',
            function ($m) {
                return strtolower($m[1]) . ":0 0" . $m[2];
            },
            $css
        );

        // Replace 0.6 to .6, but only when preceded by : or a white-space
        $css = preg_replace('/(:|\s)0+\.(\d+)/', "$1.$2", $css);

        // Shorten colors from rgb(51,102,153) to #336699
        // This makes it more likely that it'll get further compressed in the next step.
        $css = preg_replace_callback(
            '/rgb\s*\(\s*([0-9,\s]+)\s*\)/i',
            function ($m) {
                $rgbcolors = explode(',', $m[1]);
                for ($i = 0; $i < count($rgbcolors); $i = $i + 1) {
					$rgbcolors[$i] = dechex(min(255, (int)$rgbcolors[$i]));
                    
                    if (strlen($rgbcolors[$i]) === 1) {
                        $rgbcolors[$i] = '0' . $rgbcolors[$i];
                    }
                }
                return '#' . join('', $rgbcolors);
            },
            $css
        );

        // Shorten colors from #AABBCC to #ABC.
        $css = $this->_compressHexColors($css);

        // Shorten color from #f00 to red
        $css = preg_replace('/(:|\s)(#f00)(;|})/', "$1red$3", $css);
        // Other colors
        $css = preg_replace('/(:|\s)(#000080)(;|})/', "$1navy$3", $css);
        $css = preg_replace('/(:|\s)(#808080)(;|})/', "$1gray$3", $css);
        $css = preg_replace('/(:|\s)(#808000)(;|})/', "$1olive$3", $css);
        $css = preg_replace('/(:|\s)(#800080)(;|})/', "$1purple$3", $css);
        $css = preg_replace('/(:|\s)(#c0c0c0)(;|})/', "$1silver$3", $css);
        $css = preg_replace('/(:|\s)(#008080)(;|})/', "$1teal$3", $css);
        $css = preg_replace('/(:|\s)(#ffa500)(;|})/', "$1orange$3", $css);
        $css = preg_replace('/(:|\s)(#800000)(;|})/', "$1maroon$3", $css);

        // border: none -> border:0
        $css = preg_replace_callback(
            '/(border|border-top|border-right|border-bottom|border-left|outline|background):none(;|\})/i',
            function ($m) {
                return strtolower($m[1]) . ":0" . $m[2];
            },
            $css
        );

        // shorter opacity IE filter
        $css = preg_replace('/progid:DXImageTransform\.Microsoft\.Alpha\(Opacity=/i', "alpha(opacity=", $css);
		
        // Find a fraction that is used for Opera's -o-device-pixel-ratio query
        // Add token to add the "\" back in later
        $css = preg_replace('|\(([\-A-Za-z]+):([0-9]+)\/([0-9]+)\)|', '($1:$2___YUI_QUERY_FRACTION___$3)', $css);
 
        // Remove empty rules.
        $css = preg_replace('/[^\};\{\/]+\{\}/', "", $css);
 
        // Add "\" back to fix Opera -o-device-pixel-ratio query
        $css = str_replace("___YUI_QUERY_FRACTION___", "/", $css);
		

        if ($linebreakpos >= 0) {
            // Some source control tools don't like it when files containing lines longer
            // than, say 8000 characters, are checked in. The linebreak option is used in
            // that case to split long lines after a specific column.
            $startIndex = 0;
            $i = 0;
            while ($i < strlen($css)) {
                $i = $i + 1;
                if ($css[$i - 1] === '}' && $i - $startIndex > $linebreakpos) {
                    $css = substr($css, 0, $i) . "\n" . substr($css, $i);
                    $startIndex = $i;
                }
            }
        }

        // Replace multiple semi-colons in a row by a single one
        // See SF bug #1980989
        $css = preg_replace('/;;+/', ";", $css);

        // Remove line feeds
        $css = preg_replace("/\r?\n/", "", $css);

        // restore preserved comments and strings
        foreach ($this->preservedTokens as $i => $token) {
            $css = str_replace("___YUICSSMIN_PRESERVED_TOKEN_" . $i . "___", $token, $css);
        }

        // Trim the final string (for any leading or trailing white spaces)
        $css = trim($css);

        return $css;

    }

}
