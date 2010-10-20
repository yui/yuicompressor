package com.yahoo.platform.yui.compressor;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class HttpQueryFilter extends Filter {

    public static final String ENCODING = "UTF-8";

    public String description () {
        return "Parses the query from a request.";
    }

    public void doFilter (HttpExchange t, Chain chain) throws IOException {
        try {
            parseGET(t);
        } catch (UnsupportedEncodingException ex) {
            // No UTF-8? Um, ok.
            throw new IOException(ENCODING + " is not supported.", ex);
        }
        chain.doFilter(t);
    }

    public void parseGET (HttpExchange t) throws UnsupportedEncodingException {
        URI request = t.getRequestURI();
        String rawQuery = request.getRawQuery();
        Map<String, String> query = parser(rawQuery);
        t.setAttribute("query", query);
    }

    public Map<String, String> parser (String rawQuery) throws UnsupportedEncodingException {
        Map<String, String> query = new HashMap<String, String>();
        if (rawQuery == null) return query;

        StringTokenizer pairs = new StringTokenizer(rawQuery, "&");

        while (pairs.hasMoreTokens()) {
            StringTokenizer pair = new StringTokenizer(pairs.nextToken(), "=");

            String key = null;
            String val = "";

            if (pair.hasMoreTokens()) {
                key = URLDecoder.decode(pair.nextToken(), ENCODING);
            }
            if (pair.hasMoreTokens()) {
                val = URLDecoder.decode(pair.nextToken(), ENCODING);
            }

            query.put(key, val);
        }

        return query;
    }

}
