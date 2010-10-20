/*
 * YUI Compressor
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2010 Yahoo! Inc.  All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */

package com.yahoo.platform.yui.compressor;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.List;

public class YUICompressor {

    public static void main(String args[]) {
        try {

            Configuration config = new Configuration(args);

            if (config.isHelp()) {
                usage();
                System.exit(0);
            }

            if (config.getServerPort() > 0) {
                server(config);
            } else if (config.getFiles().isEmpty()) {
                throw new ConfigurationException("Filename or server option required.");
            } else {
                compress(config);
            }

        } catch (ConfigurationException e) {
            System.err.println("Error: " + e.getMessage());
            usage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void server(Configuration config) throws IOException {
        System.err.println("Server starting on port " + config.getServerPort() + ".");
        HttpHandler compressor = new CompressorHttpHandler(config);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.getServerPort()), 0);
        HttpContext context = server.createContext("/compress", compressor);
        context.getFilters().add(0, new HttpQueryFilter());
        server.setExecutor(null);
        server.start();
    }

    private static void compress(Configuration config) {
        Reader in = null;
        Writer out = null;

        String charset = config.getCharset();

        int linebreakpos = config.getLineBreak();

        String type = config.getInputType();

        List files = config.getFiles();

        String output = config.getOutput();
        String pattern[] = output != null ? output.split(":") : new String[0];

        java.util.Iterator filenames = files.iterator();

        while(filenames.hasNext()) {

            String inputFilename = (String)filenames.next();

            try {
                if (inputFilename.equals("-")) {

                    in = new InputStreamReader(System.in, charset);

                } else {

                    if (type == null) {
                        int idx = inputFilename.lastIndexOf('.');
                        if (idx >= 0 && idx < inputFilename.length() - 1) {
                            try {
                                config.setInputType(inputFilename.substring(idx + 1));
                            } catch (ConfigurationException e) {
                                usage();
                                System.exit(1);
                            }
                        }
                    }

                    in = new InputStreamReader(new FileInputStream(inputFilename), charset);
                }

                String outputFilename = output;
                // if a substitution pattern was passed in
                if (pattern.length > 1 && files.size() > 1) {
                    outputFilename = inputFilename.replaceFirst(pattern[0], pattern[1]);
                }

                if (config.isJavascript()) {

                    try {

                        JavaScriptCompressor compressor = new JavaScriptCompressor(in, new ErrorReporter() {

                            public void warning(String message, String sourceName,
                                                int line, String lineSource, int lineOffset) {
                                if (line < 0) {
                                    System.err.println("\n[WARNING] " + message);
                                } else {
                                    System.err.println("\n[WARNING] " + line + ':' + lineOffset + ':' + message);
                                }
                            }

                            public void error(String message, String sourceName,
                                              int line, String lineSource, int lineOffset) {
                                if (line < 0) {
                                    System.err.println("\n[ERROR] " + message);
                                } else {
                                    System.err.println("\n[ERROR] " + line + ':' + lineOffset + ':' + message);
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

                        boolean munge = !config.isMunge();
                        boolean preserveSemicolons = config.isPreserveSemicolons();
                        boolean disableOptimizations = config.isOptimize();
                        boolean verbose = config.isVerbose();
                        compressor.compress(out, linebreakpos, munge, verbose,
                                preserveSemicolons, disableOptimizations);

                    } catch (EvaluatorException e) {

                        e.printStackTrace();
                        // Return a special error code used specifically by the web front-end.
                        System.exit(2);

                    }

                } else if (config.isCss()) {

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
    }

    private static void usage() {
        System.err.println(
                "\nUsage: java -jar yuicompressor-x.y.z.jar [options] [input file]\n\n"

                        + "Global Options\n"
                        + "  -h, --help                Displays this information\n"
                        + "  --type <js|css>           Specifies the type of the input file\n"
                        + "  --charset <charset>       Read the input file using <charset>\n"
                        + "  --line-break <column>     Insert a line break after the specified column number\n"
                        + "  --server <port>           Start a server on <port>.\n"
                        + "  -v, --verbose             Display informational messages and warnings\n"
                        + "  -o <file>                 Place the output into <file>. Defaults to stdout.\n"
                        + "                            Multiple files can be processed using the following syntax:\n"
                        + "                            java -jar yuicompressor.jar -o '.css$:-min.css' *.css\n"
                        + "                            java -jar yuicompressor.jar -o '.js$:-min.js' *.js\n\n"

                        + "JavaScript Options\n"
                        + "  --nomunge                 Minify only, do not obfuscate\n"
                        + "  --preserve-semi           Preserve all semicolons\n"
                        + "  --disable-optimizations   Disable all micro optimizations\n\n"

                        + "If no input file is specified, it defaults to stdin. In this case, the 'type'\n"
                        + "option is required. Otherwise, the 'type' option is required only if the input\n"
                        + "file extension is neither 'js' nor 'css'.");
    }
}
