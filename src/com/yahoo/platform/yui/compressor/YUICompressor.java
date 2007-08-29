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

        if (args.length < 1) {
            usage();
            System.exit(1);
        }

        CmdLineParser parser = new CmdLineParser();
        CmdLineParser.Option typeOpt = parser.addStringOption("type");
        CmdLineParser.Option warnOpt = parser.addBooleanOption("warn");
        CmdLineParser.Option nomungeOpt = parser.addBooleanOption("nomunge");
        CmdLineParser.Option linebreakOpt = parser.addBooleanOption("line-break");
        CmdLineParser.Option helpOpt = parser.addBooleanOption('h', "help");
        CmdLineParser.Option charsetOpt = parser.addStringOption("charset");
        CmdLineParser.Option outputFilenameOpt = parser.addStringOption('o', "output");

        try {
            parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
            usage();
            System.exit(1);
        }

        String[] fileArgs = parser.getRemainingArgs();
        if (fileArgs.length == 0) {
            usage();
            System.exit(1);
        }

        Boolean help = (Boolean) parser.getOptionValue(helpOpt);
        if (help != null && help.booleanValue()) {
            usage();
            System.exit(0);
        }

        String inputFilename = fileArgs[0];

        // Get the input file extension...
        String extension = null;
        int idx = inputFilename.lastIndexOf('.');
        if (idx >= 0 && idx < inputFilename.length() - 1) {
            extension = inputFilename.substring(idx + 1);
        }

        String type = (String) parser.getOptionValue(typeOpt);
        if (type != null && !type.equalsIgnoreCase("js") && !type.equalsIgnoreCase("css")) {
            usage();
            System.exit(1);
        }

        if (extension == null && type == null) {
            System.err.println("Unknown file type. Aborting...");
            System.exit(1);
        }

        if (type != null) {
            if (type.equalsIgnoreCase("js")) {
                extension = "js";
            } else if (type.equalsIgnoreCase("css")) {
                extension = "css";
            }
        }

        String outputFilename = (String) parser.getOptionValue(outputFilenameOpt);
        if (outputFilename == null) {
            if (idx >= 0 && idx < inputFilename.length() - 1) {
                outputFilename = inputFilename.substring(0, idx) + "-min." + extension;
            } else {
                outputFilename = inputFilename + "-min." + extension;
            }
        }

        String charset = (String) parser.getOptionValue(charsetOpt);
        if (charset == null || !Charset.isSupported(charset)) {
            charset = System.getProperty("file.encoding");
            if (charset == null) {
                charset = "UTF-8";
            }
            System.out.println("\n[INFO] Using charset " + charset);
        }

        boolean linebreak = parser.getOptionValue(linebreakOpt) != null;

        Reader in = null;
        Writer out = null;

        try {

            in = new InputStreamReader(new FileInputStream(inputFilename), charset);

            if (extension.equalsIgnoreCase("js")) {
                try {
                    JavaScriptCompressor compressor = new JavaScriptCompressor(in, new ErrorReporter() {

                        public void warning(String message, String sourceName,
                                int line, String lineSource, int lineOffset) {
                            if (line < 0) {
                                System.out.println("\n" + message);
                            } else {
                                System.out.println("\n" + line + ':' + lineOffset + ':' + message);
                            }
                        }

                        public void error(String message, String sourceName,
                                int line, String lineSource, int lineOffset) {
                            if (line < 0) {
                                System.err.println("\n" + message);
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

                    // Close the input stream in case the output file should overwrite it...
                    in.close();
                    in = null;

                    boolean munge = parser.getOptionValue(nomungeOpt) == null;
                    boolean warn = parser.getOptionValue(warnOpt) != null;

                    // Open the output stream now in case it should overwrite the input...
                    out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset);

                    compressor.compress(out, linebreak, munge, warn);
                } catch (EvaluatorException e) {
                    e.printStackTrace();
                    // Return a special error code used specifically by the web front-end.
                    System.exit(2);
                }
            } else if (extension.equalsIgnoreCase("css")) {
                CssCompressor compressor = new CssCompressor(in);

                // Close the input stream in case the output file should overwrite it...
                in.close();
                in = null;

                // Open the output stream now in case it should overwrite the input...
                out = new OutputStreamWriter(new FileOutputStream(outputFilename), charset);

                compressor.compress(out, linebreak);
            }

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
                "Usage: java -jar yuicompressor.jar [options] file\n"
                        + "Options\n"
                        + "  -h, --help             Displays this information\n"
                        + "  --line-break           Insert line breaks in output for readability\n"
                        + "  --type <js|css>        Specifies the type of the input file\n"
                        + "  --charset <charset>    Read the input file using <charset>\n"
                        + "  -o <file>              Place the output into <file>\n"
                        + "  --nomunge              [js only] Minify only, do not obfuscate\n"
                        + "  --warn                 [js only] Display possible errors in the code");
    }
}
