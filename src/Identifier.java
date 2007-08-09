/*
 * YUI Compressor
 * Author: Julien Lecomte <jlecomte@yahoo-inc.com>
 * Copyright (c) 2007, Yahoo! Inc. All rights reserved.
 * Code licensed under the BSD License:
 *     http://developer.yahoo.net/yui/license.txt
 */

import org.mozilla.javascript.Token;

/**
 * Identifier represents a variable/function identifier.
 */
class Identifier extends JavaScriptToken {

    private String mungedValue;
    private ScriptOrFnScope declaredScope;

    Identifier(String value, ScriptOrFnScope declaredScope) {
        super(Token.NAME, value);
        this.declaredScope = declaredScope;
    }

    ScriptOrFnScope getDeclaredScope() {
        return declaredScope;
    }

    void setMungedValue(String value) {
        mungedValue = value;
    }

    String getMungedValue() {
        return mungedValue;
    }
}