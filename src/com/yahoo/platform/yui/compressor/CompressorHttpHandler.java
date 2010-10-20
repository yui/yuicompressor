package com.yahoo.platform.yui.compressor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONString;
import org.json.JSONStringer;
import org.mozilla.javascript.EvaluatorException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.Map;

public class CompressorHttpHandler implements HttpHandler {

    Configuration config;

    class Response implements JSONString {
        private LinkedList<String> errors = new LinkedList<String>();
        private LinkedList<String> warnings = new LinkedList<String>();
        private ByteArrayOutputStream result = new ByteArrayOutputStream();

        public LinkedList<String> getErrors() {
            return errors;
        }

        public void setErrors(LinkedList<String> errors) {
            this.errors = errors;
        }

        public LinkedList<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(LinkedList<String> warnings) {
            this.warnings = warnings;
        }

        public ByteArrayOutputStream getResult() {
            return result;
        }

        public void setResult(ByteArrayOutputStream result) {
            this.result = result;
        }

        public String toJSONString() {
            try {
                JSONArray warnings = new JSONArray(getWarnings());
                JSONArray errors = new JSONArray(getErrors());
                String result = new String(getResult().toByteArray());
                return new JSONStringer()
                    .object().key("result").value(result)
                             .key("warnings").value(warnings)
                             .key("errors").value(errors)
                    .endObject().toString();
            } catch (JSONException ex) {
                return "JSON Failure: " + ex.getMessage();
            }
        }
    }

    public CompressorHttpHandler (Configuration config) {
        config.setOutputRaw("json");
        this.config = config;
    }

    public void handle (HttpExchange t) throws IOException {
        // Inherit configuration defaults from the command line.
        // Make a clone for this request.
        Configuration config = this.config.clone();

        try {
            config = parseOptions(config, t); // get desired response format first
            if (!t.getRequestMethod().toUpperCase().equals("POST")) {
                throw new ConfigurationException("You must POST urlencoded JavaScript or CSS to this endpoint.");
            }
        } catch (ConfigurationException ex) {
            abort("Bad request", ex, HttpURLConnection.HTTP_BAD_REQUEST, config, t);
            return;
        }

        // Theory of operation:
        // InputStream to String
        // Decode
        // String to InputStream

        InputStream requestBody = t.getRequestBody();
        Reader in = new InputStreamReader(requestBody, config.getCharset());
        BufferedReader br = new BufferedReader(in);
        StringBuilder sb = new StringBuilder();
        String tmp = br.readLine();
        while (tmp != null) {
            sb.append(tmp);
            tmp = br.readLine();
        }
        String incoming = sb.toString();
        incoming = URLDecoder.decode(incoming, config.getCharset());

        // System.err.println(incoming.toCharArray());

        in = new InputStreamReader(new ByteArrayInputStream(incoming.getBytes()));

        Response response;

        try {
            response = compress(in, config);
        } catch (EvaluatorException ex) {
            // Your fault.
            abort("Syntax error", ex, HttpURLConnection.HTTP_BAD_REQUEST, config, t);
            return;
        } catch (IOException ex) {
            // My fault.
            abort("Compressor failed", ex, HttpURLConnection.HTTP_INTERNAL_ERROR, config, t);
            return;
        }

        respond(HttpURLConnection.HTTP_OK, response, config, t);
    }

    private void respond (int httpCode, Response response, Configuration config, HttpExchange t) {
        try {

            OutputStream body = t.getResponseBody();

            String outputFormat = config.getOutput();

            boolean json = false;
            if (outputFormat.equals("json")) json = true;

            byte[] resultBytes;
            String str;
            if (json) {
                str = response.toJSONString();
                resultBytes = str.getBytes();
            } else {
                if (httpCode != HttpURLConnection.HTTP_OK) {
                    str = "Error: " + response.getErrors().getFirst();
                    resultBytes = str.getBytes();
                } else {
                    resultBytes = response.getResult().toByteArray();
                    str = new String(resultBytes);
                }
            }

            t.sendResponseHeaders(httpCode, str.length());

            body.write(resultBytes);
            body.close();

        } catch (Exception ex) {
            // We can't really recover.
            System.err.println("Fatal error in HTTP server while responding to the request.");
            ex.printStackTrace();
        }
    }

    private void abort (String message, Exception ex, int httpCode, Configuration config, HttpExchange t)
            throws IOException {
        String error = message + ": " + ex.getMessage();
        // System.err.println(error);

        Response response = new Response();
        LinkedList<String> errors = new LinkedList<String>();
        errors.add(error);
        response.setErrors(errors);

        respond(httpCode, response, config, t);
    }

    private Configuration parseOptions (Configuration config, HttpExchange t)
            throws ConfigurationException, IOException {
        Map<String, String> query = (Map<String, String>) t.getAttribute("query");

        for (String key : query.keySet()) {
            String value = query.get(key);
            key = key.toLowerCase();
            value = value.toLowerCase();
            // System.err.println("parseOptions: " + key + " = " + value);
            if (key.equals("charset")) {
                config.setCharset(value);
            } else if (key.equals("output")) {
                config.setOutput(value);
            } else if (key.equals("type")) {
                config.setInputType(value);
            } else if (key.equals("lineBreak")) {
                config.setLineBreak(value);
            } else if (key.equals("semicolons")) {
                config.setPreserveSemicolons(value.equals(""));
            } else if (key.equals("munge")) {
                config.setMunge(value.equals("1") || value.equals("true"));
            } else if (key.equals("optimize")) {
                config.setOptimize(value.equals("1") || value.equals("true"));
            }
        }

        return config;
    }

    private Response compress (Reader in, Configuration config) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        Writer streamWriter = new OutputStreamWriter(result, config.getCharset());

        Response response = new Response();

        if (config.isCss()) {

            CssCompressor compressor = new CssCompressor(in);
            compressor.compress(streamWriter, config.getLineBreak());

        } else { // config.isJavascript() may also be unset. assume JS anyway.

            CompressorErrorReporter reporter = new CompressorErrorReporter();
            JavaScriptCompressor compressor = new JavaScriptCompressor(in, reporter);
            compressor.compress(streamWriter, config);
            response.setErrors(reporter.getErrors());
            response.setWarnings(reporter.getWarnings());

        }

        streamWriter.close();
        response.setResult(result);

        return response;
    }

}
