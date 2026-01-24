package org.example.protocol;

import org.example.HttpRequest;
import org.example.HttpVersion;
import org.example.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class HttpRequestParser {

    public static HttpRequest parseRequest(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {return null;}

        String[] parts = requestLine.split("\\s+");
        if (parts.length < 2) {return null;}

        HttpRequest request = new HttpRequest();
        request.method = parts[0];
        request.path =  parts[1];
        request.protocol = parts[2].startsWith("HTTP1.1") ? Protocol.HTTP1_1 : Protocol.HTTP1_0;

        request.headers = parseHeaders(reader);
        if ("POST".equalsIgnoreCase(request.method)) {
            request.hasBody = true;
        }

        // TODO parse request body
        return request;
    }

    /**
     * 找到完整 HTTP/1 请求结束位置（按 \r\n\r\n 或 Content-Length）
     * 返回 -1 表示请求未完整
     */
    public static int findHttpRequestEnd(ByteBuffer buf) {
        int startPos = buf.position();
        int limit = buf.limit();

        // 简单方式：找 \r\n\r\n
        for (int i = startPos; i < limit - 3; i++) {
            if (buf.get(i) == '\r' && buf.get(i + 1) == '\n'
                    && buf.get(i + 2) == '\r' && buf.get(i + 3) == '\n') {
                // 包含 header 长度
                int headersEnd = i + 4;

                // 检查是否有 Content-Length
                int contentLength = getContentLength(buf, startPos, headersEnd);
                if (contentLength > 0) {
                    // 确保 body 也完整
                    if (limit - headersEnd >= contentLength) {
                        return headersEnd + contentLength - startPos;
                    } else {
                        return -1; // body 未完整
                    }
                } else {
                    // 无 body，完整请求
                    return headersEnd - startPos;
                }
            }
        }

        return -1; // header 未完整
    }

    /**
     * 从 buffer 的 [start, end) 区间解析 Content-Length
     */
    public static int getContentLength(ByteBuffer buf, int start, int end) {
        byte[] headerBytes = new byte[end - start];
        int oldPos = buf.position();
        buf.position(start);
        buf.get(headerBytes);
        buf.position(oldPos);

        String headers = new String(headerBytes);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.split(":")[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    public static HttpVersion justifyHttpVersion(String requestText) {
        // get http version string
        String[] lines = requestText.split("\r\n");
        String httpVersion = lines[0].split(" ")[0];

        if (httpVersion.equals("HTTP/1.0")) {
            return HttpVersion.HTTP_1_0;
        } else {
            return HttpVersion.HTTP_1_1;
        }
    }

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

    static class InvalidHttpHeaderException extends RuntimeException {
        public InvalidHttpHeaderException(String message) {
            super(message);
        }
    }
}
