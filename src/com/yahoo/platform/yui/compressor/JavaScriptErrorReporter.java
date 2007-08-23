/*
 * YUI Compressor
 * Author: Julien Lecomte <jlecomte@yahoo-inc.com>
 * Copyright (c) 2007, Yahoo! Inc. All rights reserved.
 * Code licensed under the BSD License:
 *     http://developer.yahoo.net/yui/license.txt
 */

package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import java.io.PrintStream;

class JavaScriptErrorReporter implements ErrorReporter {

    private boolean reportWarnings;
    private PrintStream err;

    JavaScriptErrorReporter(PrintStream err, boolean reportWarnings) {
        this.err = err;
        this.reportWarnings = reportWarnings;
    }

    public void warning(String message, String sourceName,
            int line, String lineSource, int lineOffset) {
        if (reportWarnings) {
            reportMessage(message, sourceName, line, lineSource, lineOffset);
        }
    }

    public EvaluatorException runtimeError(String message, String sourceName,
            int line, String lineSource, int lineOffset) {
        error(message, sourceName, line, lineSource, lineOffset);
        return new EvaluatorException(message);
    }

    public void error(String message, String sourceName,
            int line, String lineSource, int lineOffset) {
        reportMessage(message, sourceName, line, lineSource, lineOffset);
    }

    private void reportMessage(String message, String sourceName,
            int line, String lineSource, int lineOffset) {
        if (line < 0) {
            if (message.length() == 0) {
                err.println("An unknown error occurred...");
            } else {
                err.println(message);
            }
        } else {
            err.println(line + ':' + lineOffset + ':' + message);
        }
    }
}
