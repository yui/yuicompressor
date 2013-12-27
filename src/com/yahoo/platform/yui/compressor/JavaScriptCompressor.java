/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte -  http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc.  All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Token;

public class JavaScriptCompressor {

	private static SoftReference sharedJavaScriptWords;

    private static int countChar(String haystack, char needle) {
        int idx = 0;
        int count = 0;
        int length = haystack.length();
        while (idx < length) {
            char c = haystack.charAt(idx++);
            if (c == needle) {
                count++;
            }
        }
        return count;
    }

    private static int printSourceString(String source, int offset, StringBuffer sb) {
        int length = source.charAt(offset);
        ++offset;
        if ((0x8000 & length) != 0) {
            length = ((0x7FFF & length) << 16) | source.charAt(offset);
            ++offset;
        }
        if (sb != null) {
            String str = source.substring(offset, offset + length);
            sb.append(str);
        }
        return offset + length;
    }

    private static int printSourceNumber(String source,
            int offset, StringBuffer sb) {
        double number = 0.0;
        char type = source.charAt(offset);
        ++offset;
        if (type == 'S') {
            if (sb != null) {
                number = source.charAt(offset);
            }
            ++offset;
        } else if (type == 'J' || type == 'D') {
            if (sb != null) {
                long lbits;
                lbits = (long) source.charAt(offset) << 48;
                lbits |= (long) source.charAt(offset + 1) << 32;
                lbits |= (long) source.charAt(offset + 2) << 16;
                lbits |= (long) source.charAt(offset + 3);
                if (type == 'J') {
                    number = lbits;
                } else {
                    number = Double.longBitsToDouble(lbits);
                }
            }
            offset += 4;
        } else {
            // Bad source
            throw new RuntimeException();
        }
        if (sb != null) {
            sb.append(ScriptRuntime.numberToString(number, 10));
        }
        return offset;
    }

    private static ArrayList parse(Reader in, ErrorReporter reporter)
            throws IOException, EvaluatorException {

        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_1_7);
        Parser parser = new Parser(env, reporter);
        parser.parse(in, null, 1);
        String source = parser.getEncodedSource();

        int offset = 0;
        int length = source.length();
        ArrayList tokens = new ArrayList();
        StringBuffer sb = new StringBuffer();

        while (offset < length) {
            int tt = source.charAt(offset++);
            switch (tt) {

                case Token.CONDCOMMENT:
                case Token.KEEPCOMMENT:
                case Token.NAME:
                case Token.REGEXP:
                case Token.STRING:
                    sb.setLength(0);
                    offset = printSourceString(source, offset, sb);
                    tokens.add(new JavaScriptToken(tt, sb.toString()));
                    break;

                case Token.NUMBER:
                    sb.setLength(0);
                    offset = printSourceNumber(source, offset, sb);
                    tokens.add(new JavaScriptToken(tt, sb.toString()));
                    break;

                default:
                    String literal = (String) javaScriptWords().literals.get(new Integer(tt));
                    if (literal != null) {
                        tokens.add(new JavaScriptToken(tt, literal));
                    }
                    break;
            }
        }

        return tokens;
    }

    private static void processStringLiterals(ArrayList tokens, boolean merge) {

        String tv;
        int i, length = tokens.size();
        JavaScriptToken token, prevToken, nextToken;

        if (merge) {

            // Concatenate string literals that are being appended wherever
            // it is safe to do so. Note that we take care of the case:
            //     "a" + "b".toUpperCase()

            for (i = 0; i < length; i++) {
                token = (JavaScriptToken) tokens.get(i);
                switch (token.getType()) {

                    case Token.ADD:
                        if (i > 0 && i < length) {
                            prevToken = (JavaScriptToken) tokens.get(i - 1);
                            nextToken = (JavaScriptToken) tokens.get(i + 1);
                            if (prevToken.getType() == Token.STRING && nextToken.getType() == Token.STRING &&
                                    (i == length - 1 || ((JavaScriptToken) tokens.get(i + 2)).getType() != Token.DOT)) {
                                tokens.set(i - 1, new JavaScriptToken(Token.STRING,
                                        prevToken.getValue() + nextToken.getValue()));
                                tokens.remove(i + 1);
                                tokens.remove(i);
                                i = i - 1;
                                length = length - 2;
                                break;
                            }
                        }
                }
            }

        }

        // Second pass...

        for (i = 0; i < length; i++) {
            token = (JavaScriptToken) tokens.get(i);
            if (token.getType() == Token.STRING) {
                tv = token.getValue();

                // Finally, add the quoting characters and escape the string. We use
                // the quoting character that minimizes the amount of escaping to save
                // a few additional bytes.

                char quotechar;
                int singleQuoteCount = countChar(tv, '\'');
                int doubleQuoteCount = countChar(tv, '"');
                if (doubleQuoteCount <= singleQuoteCount) {
                    quotechar = '"';
                } else {
                    quotechar = '\'';
                }

                tv = quotechar + escapeString(tv, quotechar) + quotechar;

                // String concatenation transforms the old script scheme:
                //     '<scr'+'ipt ...><'+'/script>'
                // into the following:
                //     '<script ...></script>'
                // which breaks if this code is embedded inside an HTML document.
                // Since this is not the right way to do this, let's fix the code by
                // transforming all "</script" into "<\/script"

                if (tv.indexOf("</script") >= 0) {
                    tv = tv.replaceAll("<\\/script", "<\\\\/script");
                }

                tokens.set(i, new JavaScriptToken(Token.STRING, tv));
            }
        }
    }

    // Add necessary escaping that was removed in Rhino's tokenizer.
    private static String escapeString(String s, char quotechar) {

        assert quotechar == '"' || quotechar == '\'';

        if (s == null) {
            return null;
        }

        StringBuffer sb = new StringBuffer();
        for (int i = 0, L = s.length(); i < L; i++) {
            int c = s.charAt(i);
            if (c == quotechar) {
                sb.append("\\");
            }
            sb.append((char) c);
        }

        return sb.toString();
    }

    /*
     * Simple check to see whether a string is a valid identifier name.
     * If a string matches this pattern, it means it IS a valid
     * identifier name. If a string doesn't match it, it does not
     * necessarily mean it is not a valid identifier name.
     */
    private static final Pattern SIMPLE_IDENTIFIER_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private static boolean isValidIdentifier(String s) {
        Matcher m = SIMPLE_IDENTIFIER_NAME_PATTERN.matcher(s);
        return (m.matches() && !javaScriptWords().reserved.contains(s));
    }

    /*
    * Transforms obj["foo"] into obj.foo whenever possible, saving 3 bytes.
    */
    private static void optimizeObjectMemberAccess(ArrayList tokens) {

        String tv;
        int i, length;
        JavaScriptToken token;

        for (i = 0, length = tokens.size(); i < length; i++) {

            if (((JavaScriptToken) tokens.get(i)).getType() == Token.LB &&
                    i > 0 && i < length - 2 &&
                    ((JavaScriptToken) tokens.get(i - 1)).getType() == Token.NAME &&
                    ((JavaScriptToken) tokens.get(i + 1)).getType() == Token.STRING &&
                    ((JavaScriptToken) tokens.get(i + 2)).getType() == Token.RB) {
                token = (JavaScriptToken) tokens.get(i + 1);
                tv = token.getValue();
                tv = tv.substring(1, tv.length() - 1);
                if (isValidIdentifier(tv)) {
                    tokens.set(i, new JavaScriptToken(Token.DOT, "."));
                    tokens.set(i + 1, new JavaScriptToken(Token.NAME, tv));
                    tokens.remove(i + 2);
                    i = i + 2;
                    length = length - 1;
                }
            }
        }
    }

    /*
     * Transforms 'foo': ... into foo: ... whenever possible, saving 2 bytes.
     */
    private static void optimizeObjLitMemberDecl(ArrayList tokens) {

        String tv;
        int i, length;
        JavaScriptToken token;

        for (i = 0, length = tokens.size(); i < length; i++) {
            if (((JavaScriptToken) tokens.get(i)).getType() == Token.OBJECTLIT &&
                    i > 0 && ((JavaScriptToken) tokens.get(i - 1)).getType() == Token.STRING) {
                token = (JavaScriptToken) tokens.get(i - 1);
                tv = token.getValue();
                tv = tv.substring(1, tv.length() - 1);
                if (isValidIdentifier(tv)) {
                    tokens.set(i - 1, new JavaScriptToken(Token.NAME, tv));
                }
            }
        }
    }

    private ErrorReporter logger;

    private boolean munge;
    private boolean verbose;

    private static final int BUILDING_SYMBOL_TREE = 1;
    private static final int CHECKING_SYMBOL_TREE = 2;

    private int mode;
    private int offset;
    private int braceNesting;
    private ArrayList tokens;
    private Stack scopes = new Stack();
    private ScriptOrFnScope globalScope = new ScriptOrFnScope(-1, null);
    private Hashtable indexedScopes = new Hashtable();
    
    private JavaScriptWords words;
    
    private ArrayList ones;
    private ArrayList twos;
    private ArrayList threes;

    private Set builtin = new HashSet();
    private Map literals = new Hashtable();
    private Set reserved = new HashSet();

    public JavaScriptCompressor(Reader in, ErrorReporter reporter)
            throws IOException, EvaluatorException {

    	prepareJavaScriptWords();

        this.logger = reporter;
        this.tokens = parse(in, reporter);
    }
    
    private void prepareJavaScriptWords() {
    	/* Create a strong reference to the precompute so that it isn't gc'd while we're alive */
    	words = javaScriptWords();
    
    	ones = words.ones;
    	twos = words.twos;
    	threes = words.threes;
    	builtin = words.builtin;
    	literals = words.literals;
    	reserved = words.reserved;
    }
    
    static JavaScriptWords javaScriptWords() {
    	JavaScriptWords words = sharedJavaScriptWords != null ? (JavaScriptWords) sharedJavaScriptWords.get() : null;
    	if (words == null) {
    		words = new JavaScriptWords();
    		sharedJavaScriptWords = new SoftReference(words);
    	}
    	return words;
    }

    public void compress(Writer out, Writer mungemap, int linebreak, boolean munge, boolean verbose,
            boolean preserveAllSemiColons, boolean disableOptimizations)
            throws IOException {

        this.munge = munge;
        this.verbose = verbose;

        processStringLiterals(this.tokens, !disableOptimizations);

        if (!disableOptimizations) {
            optimizeObjectMemberAccess(this.tokens);
            optimizeObjLitMemberDecl(this.tokens);
        }

        buildSymbolTree();
        // DO NOT TOUCH this.tokens BETWEEN THESE TWO PHASES (BECAUSE OF this.indexedScopes)
        mungeSymboltree();
        StringBuffer sb = printSymbolTree(linebreak, preserveAllSemiColons);

        out.write(sb.toString());

        if (mungemap != null) {
            printMungeMapping(mungemap);
        }
    }

    private ScriptOrFnScope getCurrentScope() {
        return (ScriptOrFnScope) scopes.peek();
    }

    private void enterScope(ScriptOrFnScope scope) {
        scopes.push(scope);
    }

    private void leaveCurrentScope() {
        scopes.pop();
    }

    private JavaScriptToken consumeToken() {
        return (JavaScriptToken) tokens.get(offset++);
    }

    private JavaScriptToken getToken(int delta) {
        return (JavaScriptToken) tokens.get(offset + delta);
    }

    /*
     * Returns the identifier for the specified symbol defined in
     * the specified scope or in any scope above it. Returns null
     * if this symbol does not have a corresponding identifier.
     */
    private JavaScriptIdentifier getIdentifier(String symbol, ScriptOrFnScope scope) {
        JavaScriptIdentifier identifier;
        while (scope != null) {
            identifier = scope.getIdentifier(symbol);
            if (identifier != null) {
                return identifier;
            }
            scope = scope.getParentScope();
        }
        return null;
    }

    /*
     * If either 'eval' or 'with' is used in a local scope, we must make
     * sure that all containing local scopes don't get munged. Otherwise,
     * the obfuscation would potentially introduce bugs.
     */
    private void protectScopeFromObfuscation(ScriptOrFnScope scope) {
        assert scope != null;

        if (scope == globalScope) {
            // The global scope does not get obfuscated,
            // so we don't need to worry about it...
            return;
        }

        // Find the highest local scope containing the specified scope.
        while (scope.getParentScope() != globalScope) {
            scope = scope.getParentScope();
        }

        assert scope.getParentScope() == globalScope;
        scope.preventMunging();
    }

    private String getDebugString(int max) {
        assert max > 0;
        StringBuffer result = new StringBuffer();
        int start = Math.max(offset - max, 0);
        int end = Math.min(offset + max, tokens.size());
        for (int i = start; i < end; i++) {
            JavaScriptToken token = (JavaScriptToken) tokens.get(i);
            if (i == offset - 1) {
                result.append(" ---> ");
            }
            result.append(token.getValue());
            if (i == offset - 1) {
                result.append(" <--- ");
            }
        }
        return result.toString();
    }

    private void warn(String message, boolean showDebugString) {
        if (verbose) {
            if (showDebugString) {
                message = message + "\n" + getDebugString(10);
            }
            logger.warning(message, null, -1, null, -1);
        }
    }

    private void parseFunctionDeclaration() {

        String symbol;
        JavaScriptToken token;
        ScriptOrFnScope currentScope, fnScope;
        JavaScriptIdentifier identifier;

        currentScope = getCurrentScope();

        token = consumeToken();
        if (token.getType() == Token.NAME) {
            if (mode == BUILDING_SYMBOL_TREE) {
                // Get the name of the function and declare it in the current scope.
                symbol = token.getValue();
                if (currentScope.getIdentifier(symbol) != null) {
                    warn("The function " + symbol + " has already been declared in the same scope...", true);
                }
                currentScope.declareIdentifier(symbol);
            }
            token = consumeToken();
        }

        assert token.getType() == Token.LP;
        if (mode == BUILDING_SYMBOL_TREE) {
            fnScope = new ScriptOrFnScope(braceNesting, currentScope);
            indexedScopes.put(new Integer(offset), fnScope);
        } else {
            fnScope = (ScriptOrFnScope) indexedScopes.get(new Integer(offset));
        }

        // Parse function arguments.
        int argpos = 0;
        while ((token = consumeToken()).getType() != Token.RP) {
            assert token.getType() == Token.NAME ||
                    token.getType() == Token.COMMA;
            if (token.getType() == Token.NAME && mode == BUILDING_SYMBOL_TREE) {
                symbol = token.getValue();
                identifier = fnScope.declareIdentifier(symbol);
                if (symbol.equals("$super") && argpos == 0) {
                    // Exception for Prototype 1.6...
                    identifier.preventMunging();
                }
                argpos++;
            }
        }

        token = consumeToken();
        assert token.getType() == Token.LC;
        braceNesting++;

        token = getToken(0);
        if (token.getType() == Token.STRING &&
                getToken(1).getType() == Token.SEMI) {
            // This is a hint. Hints are empty statements that look like
            // "localvar1:nomunge, localvar2:nomunge"; They allow developers
            // to prevent specific symbols from getting obfuscated (some heretic
            // implementations, such as Prototype 1.6, require specific variable
            // names, such as $super for example, in order to work appropriately.
            // Note: right now, only "nomunge" is supported in the right hand side
            // of a hint. However, in the future, the right hand side may contain
            // other values.
            consumeToken();
            String hints = token.getValue();
            // Remove the leading and trailing quotes...
            hints = hints.substring(1, hints.length() - 1).trim();
            StringTokenizer st1 = new StringTokenizer(hints, ",");
            while (st1.hasMoreTokens()) {
                String hint = st1.nextToken();
                int idx = hint.indexOf(':');
                if (idx <= 0 || idx >= hint.length() - 1) {
                    if (mode == BUILDING_SYMBOL_TREE) {
                        // No need to report the error twice, hence the test...
                        warn("Invalid hint syntax: " + hint, true);
                    }
                    break;
                }
                String variableName = hint.substring(0, idx).trim();
                String variableType = hint.substring(idx + 1).trim();
                if (mode == BUILDING_SYMBOL_TREE) {
                    fnScope.addHint(variableName, variableType);
                } else if (mode == CHECKING_SYMBOL_TREE) {
                    identifier = fnScope.getIdentifier(variableName);
                    if (identifier != null) {
                        if (variableType.equals("nomunge")) {
                            identifier.preventMunging();
                        } else {
                            warn("Unsupported hint value: " + hint, true);
                        }
                    } else {
                        warn("Hint refers to an unknown identifier: " + hint, true);
                    }
                }
            }
        }

        parseScope(fnScope);
    }

    private void parseCatch() {

        String symbol;
        JavaScriptToken token;
        ScriptOrFnScope currentScope;
        JavaScriptIdentifier identifier;

        token = getToken(-1);
        assert token.getType() == Token.CATCH;
        token = consumeToken();
        assert token.getType() == Token.LP;
        token = consumeToken();
        assert token.getType() == Token.NAME;

        symbol = token.getValue();
        currentScope = getCurrentScope();

        if (mode == BUILDING_SYMBOL_TREE) {
            // We must declare the exception identifier in the containing function
            // scope to avoid errors related to the obfuscation process. No need to
            // display a warning if the symbol was already declared here...
            currentScope.declareIdentifier(symbol);
        } else {
            identifier = getIdentifier(symbol, currentScope);
            identifier.incrementRefcount();
        }

        token = consumeToken();
        assert token.getType() == Token.RP;
    }

    private void parseExpression() {

        // Parse the expression until we encounter a comma or a semi-colon
        // in the same brace nesting, bracket nesting and paren nesting.
        // Parse functions if any...

        String symbol;
        JavaScriptToken token;
        ScriptOrFnScope currentScope;
        JavaScriptIdentifier identifier;

        int expressionBraceNesting = braceNesting;
        int bracketNesting = 0;
        int parensNesting = 0;

        int length = tokens.size();

        while (offset < length) {

            token = consumeToken();
            currentScope = getCurrentScope();

            switch (token.getType()) {

                case Token.SEMI:
                case Token.COMMA:
                    if (braceNesting == expressionBraceNesting &&
                            bracketNesting == 0 &&
                            parensNesting == 0) {
                        return;
                    }
                    break;

                case Token.FUNCTION:
                    parseFunctionDeclaration();
                    break;

                case Token.LC:
                    braceNesting++;
                    break;

                case Token.RC:
                    braceNesting--;
                    assert braceNesting >= expressionBraceNesting;
                    break;

                case Token.LB:
                    bracketNesting++;
                    break;

                case Token.RB:
                    bracketNesting--;
                    break;

                case Token.LP:
                    parensNesting++;
                    break;

                case Token.RP:
                    parensNesting--;
                    break;

                case Token.CONDCOMMENT:
                    if (mode == BUILDING_SYMBOL_TREE) {
                        protectScopeFromObfuscation(currentScope);
                        warn("Using JScript conditional comments is not recommended." + (munge ? " Moreover, using JScript conditional comments reduces the level of compression!" : ""), true);
                    }
                    break;

                case Token.NAME:
                    symbol = token.getValue();

                    if (mode == BUILDING_SYMBOL_TREE) {

                        if (symbol.equals("eval")) {

                            protectScopeFromObfuscation(currentScope);
                            warn("Using 'eval' is not recommended." + (munge ? " Moreover, using 'eval' reduces the level of compression!" : ""), true);

                        }

                    } else if (mode == CHECKING_SYMBOL_TREE) {

                        if ((offset < 2 ||
                                (getToken(-2).getType() != Token.DOT &&
                                        getToken(-2).getType() != Token.GET &&
                                        getToken(-2).getType() != Token.SET)) &&
                                getToken(0).getType() != Token.OBJECTLIT) {

                            identifier = getIdentifier(symbol, currentScope);

                            if (identifier == null) {

                                if (symbol.length() <= 3 && !builtin.contains(symbol)) {
                                    // Here, we found an undeclared and un-namespaced symbol that is
                                    // 3 characters or less in length. Declare it in the global scope.
                                    // We don't need to declare longer symbols since they won't cause
                                    // any conflict with other munged symbols.
                                    globalScope.declareIdentifier(symbol);

                                    // I removed the warning since was only being done when
                                    // for identifiers 3 chars or less, and was just causing
                                    // noise for people who happen to rely on an externally
                                    // declared variable that happen to be that short.  We either
                                    // should always warn or never warn -- the fact that we
                                    // declare the short symbols in the global space doesn't
                                    // change anything.
                                    // warn("Found an undeclared symbol: " + symbol, true);
                                }

                            } else {

                                identifier.incrementRefcount();
                            }
                        }
                    }
                    break;
            }
        }
    }

    private void parseScope(ScriptOrFnScope scope) {

        String symbol;
        JavaScriptToken token;
        JavaScriptIdentifier identifier;

        int length = tokens.size();

        enterScope(scope);

        while (offset < length) {

            token = consumeToken();

            switch (token.getType()) {

                case Token.VAR:

                    if (mode == BUILDING_SYMBOL_TREE && scope.incrementVarCount() > 1) {
                        warn("Try to use a single 'var' statement per scope.", true);
                    }

                    /* FALLSTHROUGH */

                case Token.CONST:

                    // The var keyword is followed by at least one symbol name.
                    // If several symbols follow, they are comma separated.
                    for (; ;) {
                        token = consumeToken();

                        assert token.getType() == Token.NAME;

                        if (mode == BUILDING_SYMBOL_TREE) {
                            symbol = token.getValue();
                            if (scope.getIdentifier(symbol) == null) {
                                scope.declareIdentifier(symbol);
                            } else {
                                warn("The variable " + symbol + " has already been declared in the same scope...", true);
                            }
                        }

                        token = getToken(0);

                        assert token.getType() == Token.SEMI ||
                                token.getType() == Token.ASSIGN ||
                                token.getType() == Token.COMMA ||
                                token.getType() == Token.IN;

                        if (token.getType() == Token.IN) {
                            break;
                        } else {
                            parseExpression();
                            token = getToken(-1);
                            if (token.getType() == Token.SEMI) {
                                break;
                            }
                        }
                    }
                    break;

                case Token.FUNCTION:
                    parseFunctionDeclaration();
                    break;

                case Token.LC:
                    braceNesting++;
                    break;

                case Token.RC:
                    braceNesting--;
                    assert braceNesting >= scope.getBraceNesting();
                    if (braceNesting == scope.getBraceNesting()) {
                        leaveCurrentScope();
                        return;
                    }
                    break;

                case Token.WITH:
                    if (mode == BUILDING_SYMBOL_TREE) {
                        // Inside a 'with' block, it is impossible to figure out
                        // statically whether a symbol is a local variable or an
                        // object member. As a consequence, the only thing we can
                        // do is turn the obfuscation off for the highest scope
                        // containing the 'with' block.
                        protectScopeFromObfuscation(scope);
                        warn("Using 'with' is not recommended." + (munge ? " Moreover, using 'with' reduces the level of compression!" : ""), true);
                    }
                    break;

                case Token.CATCH:
                    parseCatch();
                    break;

                case Token.CONDCOMMENT:
                    if (mode == BUILDING_SYMBOL_TREE) {
                        protectScopeFromObfuscation(scope);
                        warn("Using JScript conditional comments is not recommended." + (munge ? " Moreover, using JScript conditional comments reduces the level of compression." : ""), true);
                    }
                    break;

                case Token.NAME:
                    symbol = token.getValue();

                    if (mode == BUILDING_SYMBOL_TREE) {

                        if (symbol.equals("eval")) {

                            protectScopeFromObfuscation(scope);
                            warn("Using 'eval' is not recommended." + (munge ? " Moreover, using 'eval' reduces the level of compression!" : ""), true);

                        }

                    } else if (mode == CHECKING_SYMBOL_TREE) {

                        if ((offset < 2 || getToken(-2).getType() != Token.DOT) &&
                                getToken(0).getType() != Token.OBJECTLIT) {

                            identifier = getIdentifier(symbol, scope);

                            if (identifier == null) {

                                if (symbol.length() <= 3 && !builtin.contains(symbol)) {
                                    // Here, we found an undeclared and un-namespaced symbol that is
                                    // 3 characters or less in length. Declare it in the global scope.
                                    // We don't need to declare longer symbols since they won't cause
                                    // any conflict with other munged symbols.
                                    globalScope.declareIdentifier(symbol);
                                    // warn("Found an undeclared symbol: " + symbol, true);
                                }

                            } else {

                                identifier.incrementRefcount();
                            }
                        }
                    }
                    break;
            }
        }
    }

    private void buildSymbolTree() {
        offset = 0;
        braceNesting = 0;
        scopes.clear();
        indexedScopes.clear();
        indexedScopes.put(new Integer(0), globalScope);
        mode = BUILDING_SYMBOL_TREE;
        parseScope(globalScope);
    }

    private void mungeSymboltree() {

        if (!munge) {
            return;
        }

        // One problem with obfuscation resides in the use of undeclared
        // and un-namespaced global symbols that are 3 characters or less
        // in length. Here is an example:
        //
        //     var declaredGlobalVar;
        //
        //     function declaredGlobalFn() {
        //         var localvar;
        //         localvar = abc; // abc is an undeclared global symbol
        //     }
        //
        // In the example above, there is a slim chance that localvar may be
        // munged to 'abc', conflicting with the undeclared global symbol
        // abc, creating a potential bug. The following code detects such
        // global symbols. This must be done AFTER the entire file has been
        // parsed, and BEFORE munging the symbol tree. Note that declaring
        // extra symbols in the global scope won't hurt.
        //
        // Note: Since we go through all the tokens to do this, we also use
        // the opportunity to count how many times each identifier is used.

        offset = 0;
        braceNesting = 0;
        scopes.clear();
        mode = CHECKING_SYMBOL_TREE;
        parseScope(globalScope);
        globalScope.munge(words);
    }

    private StringBuffer printSymbolTree(int linebreakpos, boolean preserveAllSemiColons)
            throws IOException {
    	
        offset = 0;
        braceNesting = 0;
        scopes.clear();
        
    	if (tokens.isEmpty())
    		return new StringBuffer();

        String symbol;
        JavaScriptToken token;
        JavaScriptToken lastToken = getToken(0);
        ScriptOrFnScope currentScope;
        JavaScriptIdentifier identifier;

        int length = tokens.size();
        StringBuffer result = new StringBuffer();

        int linestartpos = 0;

        enterScope(globalScope);

        while (offset < length) {

            token = consumeToken();
            symbol = token.getValue();
            currentScope = getCurrentScope();
            switch (token.getType()) {
                case Token.GET:
                case Token.SET:
                    lastToken = token;

                case Token.NAME:

                    if (offset >= 2 && getToken(-2).getType() == Token.DOT ||
                            getToken(0).getType() == Token.OBJECTLIT) {

                        result.append(symbol);

                    } else {

                        identifier = getIdentifier(symbol, currentScope);
                        if (identifier != null) {
                            if (identifier.getMungedValue() != null) {
                                result.append(identifier.getMungedValue());
                            } else {
                                result.append(symbol);
                            }
                            if (currentScope != globalScope && identifier.getRefcount() == 0) {
                                warn("The symbol " + symbol + " is declared but is apparently never used.\nThis code can probably be written in a more compact way.", true);
                            }
                        } else {
                            result.append(symbol);
                        }
                    }
                    break;

                case Token.REGEXP:
                case Token.STRING:
                    result.append(symbol);
                    break;

                case Token.NUMBER:
                    if (getToken(0).getType() == Token.DOT) {
                        // calling methods on int requires a leading dot so JS doesn't
                        // treat the method as the decimal component of a float
                        result.append('(');
                        result.append(symbol);
                        result.append(')');
                    } else {
                        result.append(symbol);
                    }
                    break;

                case Token.ADD:
                case Token.SUB:
                    result.append((String) literals.get(new Integer(token.getType())));
                    if (offset < length) {
                        token = getToken(0);
                        if (token.getType() == Token.INC ||
                                token.getType() == Token.DEC ||
                                token.getType() == Token.ADD ||
                                token.getType() == Token.DEC) {
                            // Handle the case x +/- ++/-- y
                            // We must keep a white space here. Otherwise, x +++ y would be
                            // interpreted as x ++ + y by the compiler, which is a bug (due
                            // to the implicit assignment being done on the wrong variable)
                            result.append(' ');
                        } else if (token.getType() == Token.POS && getToken(-1).getType() == Token.ADD ||
                                token.getType() == Token.NEG && getToken(-1).getType() == Token.SUB) {
                            // Handle the case x + + y and x - - y
                            result.append(' ');
                        }
                    }
                    break;

                case Token.FUNCTION:
                    if (lastToken.getType() != Token.GET && lastToken.getType() != Token.SET) {
                        result.append("function");
                    }
                    lastToken = token;
                    token = consumeToken();
                    if (token.getType() == Token.NAME) {
                        result.append(' ');
                        symbol = token.getValue();
                        identifier = getIdentifier(symbol, currentScope);
                        assert identifier != null;
                        if (identifier.getMungedValue() != null) {
                            result.append(identifier.getMungedValue());
                        } else {
                            result.append(symbol);
                        }
                        if (currentScope != globalScope && identifier.getRefcount() == 0) {
                            warn("The symbol " + symbol + " is declared but is apparently never used.\nThis code can probably be written in a more compact way.", true);
                        }
                        token = consumeToken();
                    }
                    assert token.getType() == Token.LP;
                    result.append('(');
                    currentScope = (ScriptOrFnScope) indexedScopes.get(new Integer(offset));
                    enterScope(currentScope);
                    while ((token = consumeToken()).getType() != Token.RP) {
                        assert token.getType() == Token.NAME || token.getType() == Token.COMMA;
                        if (token.getType() == Token.NAME) {
                            symbol = token.getValue();
                            identifier = getIdentifier(symbol, currentScope);
                            assert identifier != null;
                            if (identifier.getMungedValue() != null) {
                                result.append(identifier.getMungedValue());
                            } else {
                                result.append(symbol);
                            }
                        } else if (token.getType() == Token.COMMA) {
                            result.append(',');
                        }
                    }
                    result.append(')');
                    token = consumeToken();
                    assert token.getType() == Token.LC;
                    result.append('{');
                    braceNesting++;
                    token = getToken(0);
                    if (token.getType() == Token.STRING &&
                            getToken(1).getType() == Token.SEMI) {
                        // This is a hint. Skip it!
                        consumeToken();
                        consumeToken();
                    }
                    break;

                case Token.RETURN:
                case Token.TYPEOF:
                    result.append(literals.get(new Integer(token.getType())));
                    // No space needed after 'return' and 'typeof' when followed
                    // by '(', '[', '{', a string or a regexp.
                    if (offset < length) {
                        token = getToken(0);
                        if (token.getType() != Token.LP &&
                                token.getType() != Token.LB &&
                                token.getType() != Token.LC &&
                                token.getType() != Token.STRING &&
                                token.getType() != Token.REGEXP &&
                                token.getType() != Token.SEMI) {
                            result.append(' ');
                        }
                    }
                    break;

                case Token.CASE:
                case Token.THROW:
                    result.append(literals.get(new Integer(token.getType())));
                    // White-space needed after 'case' and 'throw' when not followed by a string.
                    if (offset < length && getToken(0).getType() != Token.STRING) {
                        result.append(' ');
                    }
                    break;

                case Token.BREAK:
                case Token.CONTINUE:
                    result.append(literals.get(new Integer(token.getType())));
                    if (offset < length && getToken(0).getType() != Token.SEMI) {
                        // If 'break' or 'continue' is not followed by a semi-colon, it must
                        // be followed by a label, hence the need for a white space.
                        result.append(' ');
                    }
                    break;

                case Token.LC:
                    result.append('{');
                    braceNesting++;
                    break;

                case Token.RC:
                    result.append('}');
                    braceNesting--;
                    assert braceNesting >= currentScope.getBraceNesting();
                    if (braceNesting == currentScope.getBraceNesting()) {
                        leaveCurrentScope();
                    }
                    break;

                case Token.SEMI:
                    // No need to output a semi-colon if the next character is a right-curly...
                    if (preserveAllSemiColons || offset < length && getToken(0).getType() != Token.RC) {
                        result.append(';');
                    }

                    if (linebreakpos >= 0 && result.length() - linestartpos > linebreakpos) {
                        // Some source control tools don't like it when files containing lines longer
                        // than, say 8000 characters, are checked in. The linebreak option is used in
                        // that case to split long lines after a specific column.
                        result.append('\n');
                        linestartpos = result.length();
                    }
                    break;

                case Token.COMMA:
                    // No need to output a comma if the next character is a right-curly or a right-square bracket
                    if (offset < length && getToken(0).getType() != Token.RC && getToken(0).getType() != Token.RB) {
                        result.append(',');
                    }
                    break;

                case Token.CONDCOMMENT:
                case Token.KEEPCOMMENT:
                    if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                        result.append("\n");
                    }
                    result.append("/*");
                    if (token.getType() == Token.KEEPCOMMENT) {
                        result.append("!");
                    }
                    result.append(symbol);
                    result.append("*/\n");
                    break;

                default:
                    String literal = (String) literals.get(new Integer(token.getType()));
                    if (literal != null) {
                        result.append(literal);
                    } else {
                        warn("This symbol cannot be printed: " + symbol, true);
                    }
                    break;
            }
        }

        // Append a semi-colon at the end, even if unnecessary semi-colons are
        // supposed to be removed. This is especially useful when concatenating
        // several minified files (the absence of an ending semi-colon at the
        // end of one file may very likely cause a syntax error)
        if (!preserveAllSemiColons &&
                result.length() > 0 &&
                getToken(-1).getType() != Token.CONDCOMMENT &&
                getToken(-1).getType() != Token.KEEPCOMMENT) {
            if (result.charAt(result.length() - 1) == '\n') {
                result.setCharAt(result.length() - 1, ';');
            } else {
                result.append(';');
            }
        }

        return result;
    }

    private void printMungeMapping(Writer map) throws IOException {
        StringBuffer sb = new StringBuffer();
        globalScope.getFullMapping(sb, "");
        map.write(sb.toString());
    }
}
