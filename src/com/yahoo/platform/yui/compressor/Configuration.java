package com.yahoo.platform.yui.compressor;

import jargs.gnu.CmdLineParser;

import java.nio.charset.Charset;
import java.util.List;

public class Configuration implements Cloneable {

    protected String inputType;
    protected boolean javascript;
    protected boolean css;

    protected boolean verbose;
    protected boolean munge;
    protected int lineBreak;
    protected boolean preserveSemicolons;
    protected boolean optimize;
    protected boolean help;
    protected String charset;

    protected int serverPort;

    protected String output;
    protected List files;

    public Configuration (String args[]) throws ConfigurationException {
        CmdLineParser cliParser = new CmdLineParser();

        CmdLineParser.Option typeOpt = cliParser.addStringOption("type");
        CmdLineParser.Option verboseOpt = cliParser.addBooleanOption('v', "verbose");
        CmdLineParser.Option nomungeOpt = cliParser.addBooleanOption("nomunge");
        CmdLineParser.Option linebreakOpt = cliParser.addStringOption("line-break");
        CmdLineParser.Option preserveSemiOpt = cliParser.addBooleanOption("preserve-semi");
        CmdLineParser.Option disableOptimizationsOpt = cliParser.addBooleanOption("disable-optimizations");
        CmdLineParser.Option helpOpt = cliParser.addBooleanOption('h', "help");
        CmdLineParser.Option charsetOpt = cliParser.addStringOption("charset");
        CmdLineParser.Option serverOpt = cliParser.addStringOption("server");
        CmdLineParser.Option outputFilenameOpt = cliParser.addStringOption('o', "output");

        try {
            cliParser.parse(args);
        } catch (CmdLineParser.OptionException ex) {
            throw new ConfigurationException("Command line parse failed.");
        }

        setInputType((String) cliParser.getOptionValue(typeOpt));
        setVerbose(cliParser.getOptionValue(verboseOpt) != null);
        setMunge(cliParser.getOptionValue(nomungeOpt) == null);
        setLineBreak((String) cliParser.getOptionValue(linebreakOpt));
        setPreserveSemicolons(cliParser.getOptionValue(preserveSemiOpt) != null);
        setOptimize(cliParser.getOptionValue(disableOptimizationsOpt) == null);
        setHelp(cliParser.getOptionValue(helpOpt) != null);
        setCharset((String) cliParser.getOptionValue(charsetOpt));
        setServerPort((String) cliParser.getOptionValue(serverOpt));
        setOutput((String) cliParser.getOptionValue(outputFilenameOpt));
        setFiles(cliParser.getRemainingArgs());
    }

    // Used by CompressorHttpHandler to provide a request-level
    // Configuration object that inherits defaults from the CLI config.
    public Configuration clone () {
        try {
            return (Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) throws ConfigurationException {
        if (inputType != null) {
            inputType = inputType.toLowerCase();
            if (inputType.equals("js")) {
                setJavascript(true);
                setCss(false);
            } else if (inputType.equals("css")) {
                setCss(true);
                setJavascript(false);
            } else {
                throw new ConfigurationException("Bad type option.");
            }
        }
        this.inputType = inputType;
    }

    public boolean isJavascript() {
        return javascript;
    }

    public void setJavascript(boolean javascript) {
        this.javascript = javascript;
    }

    public boolean isCss() {
        return css;
    }

    public void setCss(boolean css) {
        this.css = css;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isMunge() {
        return munge;
    }

    public void setMunge(boolean munge) {
        this.munge = munge;
    }

    public int getLineBreak() {
        return lineBreak;
    }

    public void setLineBreak(int lineBreak) {
        this.lineBreak = lineBreak;
    }

    public void setLineBreak(String lineBreak) throws ConfigurationException {
        if (lineBreak == null) {
            setLineBreak(-1);
        } else {
            try {
                setLineBreak(Integer.parseInt(lineBreak, 10));
            } catch (NumberFormatException ex) {
                throw new ConfigurationException("Line break option is not a number.");
            }
        }
    }

    public boolean isPreserveSemicolons() {
        return preserveSemicolons;
    }

    public void setPreserveSemicolons(boolean preserveSemicolons) {
        this.preserveSemicolons = preserveSemicolons;
    }

    public boolean isOptimize() {
        return optimize;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        if (charset == null || !Charset.isSupported(charset)) {
            charset = "UTF-8";
            // System.err.println("Using UTF-8");
            if (this.isVerbose()) {
                // TODO Log!
            }
        }
        this.charset = charset;
    }

    public void setServerPort(String serverPort) throws ConfigurationException {
        if (serverPort == null) return;
        try {
            setServerPort(Integer.parseInt(serverPort, 10));
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Server port is not a number.");
        }
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getOutput() {
        return output;
    }

    public void setOutputRaw(String output) {
        this.output = output;
    }

    public void setOutput(String output) throws ConfigurationException {
        if (output != null) {
            output = output.toLowerCase();
            if (
                    !( output.equals("json") || output.equals("raw") )
                    && (getServerPort() > 0)
               ) {
                throw new ConfigurationException("In server mode, only json or raw output types are allowed.");
            }
        }
        setOutputRaw(output);
    }

    public List getFiles() {
        return files;
    }

    public void setFiles(List files) {
        if (files.isEmpty()) {
            files = new java.util.ArrayList();
            files.add("-"); // read from stdin
        }
        this.files = files;
    }

    public void setFiles(String[] files) {
        setFiles(java.util.Arrays.asList(files));
    }

}
