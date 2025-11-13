package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

public class HttpHeaderParser {

    public static Map<String, String> parseHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        StringBuilder foldedValue = null;
        String currentKey = null;

        while((line = reader.readLine()) != null) {
            // http ended with empty line
            if (line.isEmpty()) {
                break;
            }

            // process line breaking(RFC 822)
            if ((line.startsWith(" ") || line.startsWith("\t")) && currentKey != null) {
                if (foldedValue == null) {
                    foldedValue = new StringBuilder(headers.get(currentKey));
                }
                foldedValue.append(" ").append(line.trim());
                continue;
            }

            if (foldedValue != null) {
                headers.put(currentKey, foldedValue.toString());
                foldedValue = null;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                continue;
            }

            currentKey = line.substring(0, colonIndex).trim().toLowerCase(Locale.US);
            String value = line.substring(colonIndex + 1).trim();

            if ("host".equals(currentKey) && !headers.containsKey("host")) {
                // remove port(if exist)
                int portIndex = value.indexOf(':');
                if (portIndex != -1) {
                    value = value.substring(0, portIndex);
                }
            }

            headers.put(currentKey, value);
        }

        if (foldedValue != null) {
            headers.put(currentKey, foldedValue.toString());
        }

        if (!headers.containsKey("host")) {
            throw new InvalidHttpHeaderException("Missing required Host header");
        }

        return Collections.unmodifiableMap(headers);
    }

    public static int getContentLength(Map<String, String> headers) {
        try {
            return headers.containsKey("content-length") ?
                    Integer.parseInt(headers.get("content-length")) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String getContentType(Map<String, String> headers) {
        String fullType =  headers.get("content-type");
        return fullType == null ? fullType.split(";")[0].trim() :
                "application/octet-stream";
    }

    static class InvalidHttpHeaderException extends RuntimeException {
        public InvalidHttpHeaderException(String message) {
            super(message);
        }
    }
}
