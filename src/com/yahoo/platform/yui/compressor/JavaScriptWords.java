package com.yahoo.platform.yui.compressor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Token;

public class JavaScriptWords {

	final ArrayList ones;
    final ArrayList twos;
    final ArrayList threes;

    final Set builtin = new HashSet();
    final Map literals = new Hashtable();
    final Set reserved = new HashSet();

    public JavaScriptWords() {
        // This list contains all the 3 characters or less built-in global
        // symbols available in a browser. Please add to this list if you
        // see anything missing.
        builtin.add("NaN");
        builtin.add("top");

        ones = new ArrayList();
        for (char c = 'a'; c <= 'z'; c++)
            ones.add(Character.toString(c));
        for (char c = 'A'; c <= 'Z'; c++)
            ones.add(Character.toString(c));

        twos = new ArrayList();
        for (int i = 0; i < ones.size(); i++) {
            String one = (String) ones.get(i);
            for (char c = 'a'; c <= 'z'; c++)
                twos.add(one + Character.toString(c));
            for (char c = 'A'; c <= 'Z'; c++)
                twos.add(one + Character.toString(c));
            for (char c = '0'; c <= '9'; c++)
                twos.add(one + Character.toString(c));
        }

        // Remove two-letter JavaScript reserved words and built-in globals...
        twos.remove("as");
        twos.remove("is");
        twos.remove("do");
        twos.remove("if");
        twos.remove("in");
        twos.removeAll(builtin);

        threes = new ArrayList();
        for (int i = 0; i < twos.size(); i++) {
            String two = (String) twos.get(i);
            for (char c = 'a'; c <= 'z'; c++)
                threes.add(two + Character.toString(c));
            for (char c = 'A'; c <= 'Z'; c++)
                threes.add(two + Character.toString(c));
            for (char c = '0'; c <= '9'; c++)
                threes.add(two + Character.toString(c));
        }

        // Remove three-letter JavaScript reserved words and built-in globals...
        threes.remove("for");
        threes.remove("int");
        threes.remove("new");
        threes.remove("try");
        threes.remove("use");
        threes.remove("var");
        threes.removeAll(builtin);

        // That's up to ((26+26)*(1+(26+26+10)))*(1+(26+26+10))-8
        // (206,380 symbols per scope)

        // The following list comes from org/mozilla/javascript/Decompiler.java...
        literals.put(new Integer(Token.GET), "get ");
        literals.put(new Integer(Token.SET), "set ");
        literals.put(new Integer(Token.TRUE), "true");
        literals.put(new Integer(Token.FALSE), "false");
        literals.put(new Integer(Token.NULL), "null");
        literals.put(new Integer(Token.THIS), "this");
        literals.put(new Integer(Token.FUNCTION), "function");
        literals.put(new Integer(Token.COMMA), ",");
        literals.put(new Integer(Token.LC), "{");
        literals.put(new Integer(Token.RC), "}");
        literals.put(new Integer(Token.LP), "(");
        literals.put(new Integer(Token.RP), ")");
        literals.put(new Integer(Token.LB), "[");
        literals.put(new Integer(Token.RB), "]");
        literals.put(new Integer(Token.DOT), ".");
        literals.put(new Integer(Token.NEW), "new ");
        literals.put(new Integer(Token.DELPROP), "delete ");
        literals.put(new Integer(Token.IF), "if");
        literals.put(new Integer(Token.ELSE), "else");
        literals.put(new Integer(Token.FOR), "for");
        literals.put(new Integer(Token.IN), " in ");
        literals.put(new Integer(Token.WITH), "with");
        literals.put(new Integer(Token.WHILE), "while");
        literals.put(new Integer(Token.DO), "do");
        literals.put(new Integer(Token.TRY), "try");
        literals.put(new Integer(Token.CATCH), "catch");
        literals.put(new Integer(Token.FINALLY), "finally");
        literals.put(new Integer(Token.THROW), "throw");
        literals.put(new Integer(Token.SWITCH), "switch");
        literals.put(new Integer(Token.BREAK), "break");
        literals.put(new Integer(Token.CONTINUE), "continue");
        literals.put(new Integer(Token.CASE), "case");
        literals.put(new Integer(Token.DEFAULT), "default");
        literals.put(new Integer(Token.RETURN), "return");
        literals.put(new Integer(Token.VAR), "var ");
        literals.put(new Integer(Token.SEMI), ";");
        literals.put(new Integer(Token.ASSIGN), "=");
        literals.put(new Integer(Token.ASSIGN_ADD), "+=");
        literals.put(new Integer(Token.ASSIGN_SUB), "-=");
        literals.put(new Integer(Token.ASSIGN_MUL), "*=");
        literals.put(new Integer(Token.ASSIGN_DIV), "/=");
        literals.put(new Integer(Token.ASSIGN_MOD), "%=");
        literals.put(new Integer(Token.ASSIGN_BITOR), "|=");
        literals.put(new Integer(Token.ASSIGN_BITXOR), "^=");
        literals.put(new Integer(Token.ASSIGN_BITAND), "&=");
        literals.put(new Integer(Token.ASSIGN_LSH), "<<=");
        literals.put(new Integer(Token.ASSIGN_RSH), ">>=");
        literals.put(new Integer(Token.ASSIGN_URSH), ">>>=");
        literals.put(new Integer(Token.HOOK), "?");
        literals.put(new Integer(Token.OBJECTLIT), ":");
        literals.put(new Integer(Token.COLON), ":");
        literals.put(new Integer(Token.OR), "||");
        literals.put(new Integer(Token.AND), "&&");
        literals.put(new Integer(Token.BITOR), "|");
        literals.put(new Integer(Token.BITXOR), "^");
        literals.put(new Integer(Token.BITAND), "&");
        literals.put(new Integer(Token.SHEQ), "===");
        literals.put(new Integer(Token.SHNE), "!==");
        literals.put(new Integer(Token.EQ), "==");
        literals.put(new Integer(Token.NE), "!=");
        literals.put(new Integer(Token.LE), "<=");
        literals.put(new Integer(Token.LT), "<");
        literals.put(new Integer(Token.GE), ">=");
        literals.put(new Integer(Token.GT), ">");
        literals.put(new Integer(Token.INSTANCEOF), " instanceof ");
        literals.put(new Integer(Token.LSH), "<<");
        literals.put(new Integer(Token.RSH), ">>");
        literals.put(new Integer(Token.URSH), ">>>");
        literals.put(new Integer(Token.TYPEOF), "typeof");
        literals.put(new Integer(Token.VOID), "void ");
        literals.put(new Integer(Token.CONST), "const ");
        literals.put(new Integer(Token.NOT), "!");
        literals.put(new Integer(Token.BITNOT), "~");
        literals.put(new Integer(Token.POS), "+");
        literals.put(new Integer(Token.NEG), "-");
        literals.put(new Integer(Token.INC), "++");
        literals.put(new Integer(Token.DEC), "--");
        literals.put(new Integer(Token.ADD), "+");
        literals.put(new Integer(Token.SUB), "-");
        literals.put(new Integer(Token.MUL), "*");
        literals.put(new Integer(Token.DIV), "/");
        literals.put(new Integer(Token.MOD), "%");
        literals.put(new Integer(Token.COLONCOLON), "::");
        literals.put(new Integer(Token.DOTDOT), "..");
        literals.put(new Integer(Token.DOTQUERY), ".(");
        literals.put(new Integer(Token.XMLATTR), "@");
        literals.put(new Integer(Token.LET), "let ");
        literals.put(new Integer(Token.YIELD), "yield ");

        // See http://developer.mozilla.org/en/docs/Core_JavaScript_1.5_Reference:Reserved_Words

        // JavaScript 1.5 reserved words
        reserved.add("break");
        reserved.add("case");
        reserved.add("catch");
        reserved.add("continue");
        reserved.add("default");
        reserved.add("delete");
        reserved.add("do");
        reserved.add("else");
        reserved.add("finally");
        reserved.add("for");
        reserved.add("function");
        reserved.add("if");
        reserved.add("in");
        reserved.add("instanceof");
        reserved.add("new");
        reserved.add("return");
        reserved.add("switch");
        reserved.add("this");
        reserved.add("throw");
        reserved.add("try");
        reserved.add("typeof");
        reserved.add("var");
        reserved.add("void");
        reserved.add("while");
        reserved.add("with");
        // Words reserved for future use
        reserved.add("abstract");
        reserved.add("boolean");
        reserved.add("byte");
        reserved.add("char");
        reserved.add("class");
        reserved.add("const");
        reserved.add("debugger");
        reserved.add("double");
        reserved.add("enum");
        reserved.add("export");
        reserved.add("extends");
        reserved.add("final");
        reserved.add("float");
        reserved.add("goto");
        reserved.add("implements");
        reserved.add("import");
        reserved.add("int");
        reserved.add("interface");
        reserved.add("long");
        reserved.add("native");
        reserved.add("package");
        reserved.add("private");
        reserved.add("protected");
        reserved.add("public");
        reserved.add("short");
        reserved.add("static");
        reserved.add("super");
        reserved.add("synchronized");
        reserved.add("throws");
        reserved.add("transient");
        reserved.add("volatile");
        // These are not reserved, but should be taken into account
        // in isValidIdentifier (See jslint source code)
        reserved.add("arguments");
        reserved.add("eval");
        reserved.add("true");
        reserved.add("false");
        reserved.add("Infinity");
        reserved.add("NaN");
        reserved.add("null");
        reserved.add("undefined");
    }
    
}
