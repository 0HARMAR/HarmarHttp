package org.example;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StdHeadersHandler {
    // Host Header
    public static boolean haveHostHeader(Map<String, String> headers) {
        String host = headers.get("Host");
        if (host != null || host.isEmpty()) {
            return true;
        }
        return false;
    }

    // Cookie Header
    public static Map<String, String> parseCookies(String cookieContent) {
        Map<String, String> cookies = new HashMap<String, String>();
        String[] cookiesArray = cookieContent.split(";");
        for (String cookie : cookiesArray) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2) {
                cookies.put(parts[0].trim(), parts[1]);
            }
        }
        return cookies;
    }
}
