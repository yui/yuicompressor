/*
 * YUI Compressor
 * Author: Julien Lecomte -  http://www.julienlecomte.net/
 * Author: Isaac Schlueter - http://foohack.com/ 
 * Author: Stoyan Stefanov - http://phpied.com/
 * Copyright (c) 2009 Yahoo! Inc.  All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */

package com.yahoo.platform.yui.compressor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList; 

public class CssCompressor {

    private StringBuffer srcsb = new StringBuffer();

    public CssCompressor(Reader in) throws IOException {
        // Read the stream...
        int c;
        while ((c = in.read()) != -1) {
            srcsb.append((char) c);
        }
    }

    public void compress(Writer out, int linebreakpos)
            throws IOException {

        Pattern p;
        Matcher m;
        String css, token;
        StringBuffer sb;
        int startIndex, endIndex;
        ArrayList preservedTokens;

        // preserve strings so their content doesn't get accidentally minified
        preservedTokens = new ArrayList(0);
        css = srcsb.toString();
        sb = new StringBuffer();
        p = Pattern.compile("(\"([^\\\\\"]|\\\\.|\\\\)*\")|(\'([^\\\\\']|\\\\.|\\\\)*\')");
        m = p.matcher(css);
        while (m.find()) {
            token = m.group();
            char quote = token.charAt(0);
            token = token.substring(1, token.length() - 1);
            preservedTokens.add(token);
            String preserver = quote + "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___" + quote;
            m.appendReplacement(sb, preserver);
        }
        m.appendTail(sb);

        // Remove all comment blocks...
        startIndex = 0;
        boolean iemac = false;
        boolean preserve = false;
        while ((startIndex = sb.indexOf("/*", startIndex)) >= 0) {
            preserve = sb.length() > startIndex + 2 && sb.charAt(startIndex + 2) == '!';
            endIndex = sb.indexOf("*/", startIndex + 2);
            if (endIndex < 0) {
                if (!preserve) {
                    sb.delete(startIndex, sb.length());
                }
            } else if (endIndex >= startIndex + 2) {
                if (sb.charAt(endIndex-1) == '\\') {
                    // Looks like a comment to hide rules from IE Mac.
                    // Leave this comment, and the following one, but shorten them.
                    sb.replace(startIndex, endIndex + 2, "/*\\*/");
                    startIndex += 5;
                    iemac = true;
                } else if (iemac && !preserve) {
                    sb.replace(startIndex, endIndex + 2, "/**/");
                    startIndex += 4;
                    iemac = false;
                } else if (!preserve) {
                    sb.delete(startIndex, endIndex + 2);
                } else {                    
                    // preserve
                    token = sb.substring(startIndex + 3, endIndex); // 3 is "/*!".length
                    preservedTokens.add(token);
                    token = "___YUICSSMIN_PRESERVED_TOKEN_" + (preservedTokens.size() - 1) + "___";
                    sb.replace(startIndex + 2, endIndex, token);
                    if (iemac) iemac = false;
                    startIndex += 2;
                }
            }
        }

        css = sb.toString();

        // Normalize all whitespace strings to single spaces. Easier to work with that way.
        css = css.replaceAll("\\s+", " ");

        // Remove the spaces before the things that should not have spaces before them.
        // But, be careful not to turn "p :link {...}" into "p:link{...}"
        // Swap out any pseudo-class colons with the token, and then swap back.
        sb = new StringBuffer();
        p = Pattern.compile("(^|\\})(([^\\{:])+:)+([^\\{]*\\{)");
        m = p.matcher(css);
        while (m.find()) {
            String s = m.group();
            s = s.replaceAll(":", "___YUICSSMIN_PSEUDOCLASSCOLON___");
            m.appendReplacement(sb, s);
        }
        m.appendTail(sb);
        css = sb.toString();
        // Remove spaces before the things that should not have spaces before them.
        css = css.replaceAll("\\s+([!{};:>+\\(\\)\\],])", "$1");
        // bring back the colon
        css = css.replaceAll("___YUICSSMIN_PSEUDOCLASSCOLON___", ":");
        
        // retain space for special IE6 cases
        css = css.replaceAll(":first\\-(line|letter)(\\{|,)", ":first-$1 $2");
        
        // no space after the end of a preserved comment
        css = css.replaceAll("\\*/ ", "*/"); 
        
        // If there is a @charset, then only allow one, and push to the top of the file.
        css = css.replaceAll("^(.*)(@charset \"[^\"]*\";)", "$2$1");
        css = css.replaceAll("^(\\s*@charset [^;]+;\\s*)+", "$1");
        
        // Put the space back in some cases, to support stuff like
        // @media screen and (-webkit-min-device-pixel-ratio:0){
        css = css.replaceAll("\\band\\(", "and (");       

        // Remove the spaces after the things that should not have spaces after them.
        css = css.replaceAll("([!{}:;>+\\(\\[,])\\s+", "$1");

        // remove unnecessary semicolons
        css = css.replaceAll(";+}", "}");

        // Replace 0(px,em,%) with 0.
        css = css.replaceAll("([\\s:])(0)(px|em|%|in|cm|mm|pc|pt|ex)", "$1$2");

        // Replace 0 0 0 0; with 0.
        css = css.replaceAll(":0 0 0 0;", ":0;");
        css = css.replaceAll(":0 0 0;", ":0;");
        css = css.replaceAll(":0 0;", ":0;");
        // Replace background-position:0; with background-position:0 0;
        css = css.replaceAll("background-position:0;", "background-position:0 0;");

        // Replace 0.6 to .6, but only when preceded by : or a white-space
        css = css.replaceAll("(:|\\s)0+\\.(\\d+)", "$1.$2");

        // Shorten colors from rgb(51,102,153) to #336699
        // This makes it more likely that it'll get further compressed in the next step.
        p = Pattern.compile("rgb\\s*\\(\\s*([0-9,\\s]+)\\s*\\)");
        m = p.matcher(css);
        sb = new StringBuffer();
        while (m.find()) {
            String[] rgbcolors = m.group(1).split(",");
            StringBuffer hexcolor = new StringBuffer("#");
            for (int i = 0; i < rgbcolors.length; i++) {
                int val = Integer.parseInt(rgbcolors[i]);
                if (val < 16) {
                    hexcolor.append("0");
                }
                hexcolor.append(Integer.toHexString(val));
            }
            m.appendReplacement(sb, hexcolor.toString());
        }
        m.appendTail(sb);
        css = sb.toString();

        // Shorten colors from #AABBCC to #ABC. Note that we want to make sure
        // the color is not preceded by either ", " or =. Indeed, the property
        //     filter: chroma(color="#FFFFFF");
        // would become
        //     filter: chroma(color="#FFF");
        // which makes the filter break in IE.
        p = Pattern.compile("([^\"'=\\s])(\\s*)#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])");
        m = p.matcher(css);
        sb = new StringBuffer();
        while (m.find()) {
            // Test for AABBCC pattern
            if (m.group(3).equalsIgnoreCase(m.group(4)) &&
                    m.group(5).equalsIgnoreCase(m.group(6)) &&
                    m.group(7).equalsIgnoreCase(m.group(8))) {
                m.appendReplacement(sb, m.group(1) + m.group(2) + "#" + m.group(3) + m.group(5) + m.group(7));
            } else {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        css = sb.toString();

        // Remove empty rules.
        css = css.replaceAll("[^\\}\\{/;]+\\{\\}", "");

        if (linebreakpos >= 0) {
            // Some source control tools don't like it when files containing lines longer
            // than, say 8000 characters, are checked in. The linebreak option is used in
            // that case to split long lines after a specific column.
            int i = 0;
            int linestartpos = 0;
            sb = new StringBuffer(css);
            while (i < sb.length()) {
                char c = sb.charAt(i++);
                if (c == '}' && i - linestartpos > linebreakpos) {
                    sb.insert(i, '\n');
                    linestartpos = i;
                }
            }

            css = sb.toString();
        }

        // Replace multiple semi-colons in a row by a single one
        // See SF bug #1980989
        css = css.replaceAll(";;+", ";");

        // restore preserved comments and strings
        for(int i = 0, max = preservedTokens.size(); i < max; i++) {
            css = css.replace("___YUICSSMIN_PRESERVED_TOKEN_" + i + "___", preservedTokens.get(i).toString());
        }

        // Trim the final string (for any leading or trailing white spaces)
        css = css.trim();

        // Write the output...
        out.write(css);
    }
}
