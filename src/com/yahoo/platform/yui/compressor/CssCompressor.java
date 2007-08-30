/*
 * YUI Compressor
 * Author: Julien Lecomte <jlecomte@yahoo-inc.com>
 * Copyright (c) 2007, Yahoo! Inc. All rights reserved.
 * Code licensed under the BSD License:
 *     http://developer.yahoo.net/yui/license.txt
 *
 * This code is a port of Isaac Schlueter's cssmin utility.
 */

package com.yahoo.platform.yui.compressor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CssCompressor {

    private StringBuffer srcsb = new StringBuffer();

    public CssCompressor(Reader in) throws IOException {
        // Read the stream...
        int c;
        while ((c = in.read()) != -1) {
            srcsb.append((char) c);
        }
    }

    public void compress(Writer out, boolean linebreak)
            throws IOException {

        Pattern p;
        Matcher m;
        String css;
        StringBuffer sb;
        int startIndex, endIndex;

        // Remove all comment blocks...
        sb = new StringBuffer(srcsb.toString());
        while ((startIndex = sb.indexOf("/*")) >= 0) {
            endIndex = sb.indexOf("*/", startIndex + 2);
            if (endIndex >= startIndex + 2)
                sb.delete(startIndex, endIndex + 2);
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
            s = s.replaceAll(":", "___PSEUDOCLASSCOLON___");
            m.appendReplacement(sb, s);
        }
        m.appendTail(sb);
        css = sb.toString();
        css = css.replaceAll("\\s+([!{};:>+\\(\\)\\],])", "$1");
        css = css.replaceAll("___PSEUDOCLASSCOLON___", ":");

        // Remove the spaces after the things that should not have spaces after them.
        css = css.replaceAll("([!{}:;>+\\(\\[,])\\s+", "$1");

        // Add the semicolon where it's missing.
        css = css.replaceAll("([^;\\}])}", "$1;}");

        // Replace 0(px,em,%) with 0.
        css = css.replaceAll("([\\s:])(0)(px|em|%|pt|ex)", "$1$2");

        // Replace 0 0 0 0; with 0.
        css = css.replaceAll(":0 0 0 0;", ":0;");
        css = css.replaceAll(":0 0 0;", ":0;");
        css = css.replaceAll(":0 0;", ":0;");
        // Replace background-position:0; with background-position:0 0;
        css = css.replaceAll("background-position:0;", "background-position:0 0;");

        // Shorten colors from #AABBCC to #ABC
        p = Pattern.compile("#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])");
        m = p.matcher(css);
        sb = new StringBuffer();
        while (m.find()) {
            // Test for AABBCC pattern
            if (m.group(1).equalsIgnoreCase(m.group(2)) &&
                    m.group(3).equalsIgnoreCase(m.group(4)) &&
                    m.group(5).equalsIgnoreCase(m.group(6))) {
                m.appendReplacement(sb, "#" + m.group(1) + m.group(3) + m.group(5));
            } else {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        css = sb.toString();

        // Remove empty rules.
        css = css.replaceAll("[^\\}]+\\{;\\}", "");

        if (linebreak) {
            // Put a line break after each rule.
            css = css.replaceAll("}", "}\n");
        }

        // Trim the final string (for any leading white space)
        css = css.trim();

        // Write the output...
        out.write(css);
    }
}
