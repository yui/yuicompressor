/*
 * YUI Compressor
 * Author: Julien Lecomte <jlecomte@yahoo-inc.com>
 * Copyright (c) 2007, Yahoo! Inc. All rights reserved.
 * Code licensed under the BSD License:
 *     http://developer.yahoo.net/yui/license.txt
 */

package com.yahoo.platform.yui.compressor;

import jargs.gnu.CmdLineParser;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import java.io.*;
import java.nio.charset.Charset;

public class YUICompressor {

    public static void main(String args[]) {

        CmdLineParser parser = new CmdLineParser();
        CmdLineParser.Option typeOpt = parser.addStringOption("type");
        CmdLineParser.Option warnOpt = parser.addBooleanOption("warn");
        CmdLineParser.Option nomungeOpt = parser.addBooleanOption("nomunge");
        CmdLineParser.Option linebreakOpt = parser.addStringOption("line-break");
        CmdLineParser.Option preserveSemiOpt = parser.addBooleanOption("preserve-semi");
        CmdLineParser.Option preserveStringsOpt = parser.addBooleanOption("preserve-strings");
        CmdLineParser.Option helpOpt = parser.addBooleanOption('h', "help");
        CmdLineParser.Option charsetOpt = parser.addStringOption("charset");
        CmdLineParser.Option outputFilenameOpt = parser.addStringOption('o', "output");

        Reader in = null;
        Writer out = null;

        try {

            parser.parse(args);

            Boolean help = (Boolean) parser.getOptionValue(helpOpt);
            if (help != null && help.booleanValue()) {
                usage();
                System.exit(0);
            }

            String charset = (String) parser.getOptionValue(charsetOpt);
            if (charset == null || !Charset.isSupported(charset)) {
                charset = System.getProperty("file.encoding");
                if (charset == null) {
                    charset = "UTF-8";
                }
                System.err.println("\n[INFO] Using charset " + charset);
            }

            String[] fileArgs = parser.getRemainingArgs();
            String type = (String) parser.getOptionValue(typeOpt);

            if (fileArgs.length == 0) {

                if (type == null || !type.equalsIgnoreCase("js") && !type.equalsIgnoreCase("css")) {
                    usage();
                    System.exit(1);
                }

                in = new InputStreamReader(System.in, charset);

            } else {

                if (type != null && !type.equalsIgnoreCase("js") && !type.equalsIgnoreCase("css")) {
                    usage();
                    System.exit(1);
                }

                String inputFilename = fileArgs[0];

                if (type == null) {
                    int idx = inputFilename.lastIndexOf('.');
                    if (idx >= 0 && idx < inputFilename.length() - 1) {
                        type = inputFilename.substring(idx + 1);
                    }
                }

                if (type == null || !type.equalsIgnoreCase("js") && !type.equalsIgnoreCase("css")) {
                    usage();
                    System.exit(1);
                }

                in = new InputStreamReader(new FileInputStream(inputFilename), charset);
            }

            int linebreakpos = -1;
            String linebreakstr = (String) parser.getOptionValue(linebreakOpt);
            if (linebreakstr != null) {
                try {
                    linebreakpos = Integer.parseInt(linebreakstr, 10);
                } catch (NumberFormatException e) {
                    usage();
                    System.exit(1);
                }
            }

            String outputFilename = (String) parser.getOptionValue(outputFilenameOpt);

            if (type.equalsIgnoreCase("js")) {

                try {

                    JavaScriptCompressor compressor = new JavaScriptCompressor(in, new ErrorReporter() {

                        public void warning(String message, String sourceName,
                                int line, String lineSource, int lineOffset) {
                            if (line < 0) {
                                System.err.println("\n[WARNING] " + message);
                            } else {
                                System.err.println("\n" + line + ':' + lineOffset + ':' + message);
                            }
                        }

                        public void error(String message, String sourceName,
                                int line, String lineSource, int lineOffset) {
                            if (line < 0) {
                                System.err.println("\n[ERROR] " + message);
                            } else {
                                System.err.println("\n" + line + ':' + lineOffset + ':' + message);
                            }
                        }

                        public EvaluatorException runtimeError(String message, String sourceName,
                                int line, String lineSource, int lineOffset) {
                            error(message, sourceName, line, lineSource, lineOffset);
                            return new EvaluatorException(message);
                        }
                    });

                    // Close the input stream first, and then open the output stream,
                    // in case the output file should override the input file.
                    in.close(); in = null;

                    if (outputFilename == null) {
                        out = new OutputStreamWriter(System.out, charset);
                    } else {
                        out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset);
                    }

                    boolean munge = parser.getOptionValue(nomungeOpt) == null;
                    boolean warn = parser.getOptionValue(warnOpt) != null;
                    boolean preserveAllSemiColons = parser.getOptionValue(preserveSemiOpt) != null;
                    boolean mergeStringLiterals = parser.getOptionValue(preserveStringsOpt) == null;

                    compressor.compress(out, linebreakpos, munge, warn,
                            preserveAllSemiColons, mergeStringLiterals);

                } catch (EvaluatorException e) {

                    e.printStackTrace();
                    // Return a special error code used specifically by the web front-end.
                    System.exit(2);

                }

            } else if (type.equalsIgnoreCase("css")) {

                CssCompressor compressor = new CssCompressor(in);

                // Close the input stream first, and then open the output stream,
                // in case the output file should override the input file.
                in.close(); in = null;

                if (outputFilename == null) {
                    out = new OutputStreamWriter(System.out, charset);
                } else {
                    out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset);
                }

                compressor.compress(out, linebreakpos);
            }

        } catch (CmdLineParser.OptionException e) {

            usage();
            System.exit(1);

        } catch (IOException e) {

            e.printStackTrace();
            System.exit(1);

        } finally {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void usage() {
        System.out.println(
                "\nUsage: java -jar yuicompressor-x.y.z.jar [options] [input file]\n\n"

                        + "Global Options\n"
                        + "  -h, --help                   Displays this information\n"
                        + "  --type <js|css>              Specifies the type of the input file\n"
                        + "  --charset <charset>          Read the input file using <charset>\n"
                        + "  --line-break <column>        Insert a line break after the specified column number\n"
                        + "  -o <file>                    Place the output into <file>. Defaults to stdout.\n\n"

                        + "JavaScript Options\n"
                        + "  --warn                       Display possible errors in the code\n"
                        + "  --nomunge                    Minify only, do not obfuscate\n"
                        + "  --preserve-semi              Preserve all semicolons\n"
                        + "  --preserve-strings           Do not merge string literals\n\n"

                        + "If no input file is specified, it defaults to stdin. In this case, the 'type'\n"
                        + "option is required. Otherwise, the 'type' option is required only if the input\n"
                        + "file extension is neither 'js' nor 'css'.");
    }
}
