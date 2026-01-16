package org.example.http2;

import java.util.HashMap;
import java.util.Map;

public class StaticTable {
    // --------- 静态表 (RFC 7541 §2.3.1) ---------
    public static final String[][] TABLE = new String[][] {
            {":authority", ""},
            {":method", "GET"},
            {":method", "POST"},
            {":path", "/"},
            {":path", "/index.html"},
            {":scheme", "http"},
            {":scheme", "https"},
            {":status", "200"},
            {":status", "204"},
            {":status", "206"},
            {":status", "304"},
            {":status", "400"},
            {":status", "404"},
            {":status", "500"},
            {"accept-charset", ""},
            {"accept-encoding", "gzip, deflate"},
            {"accept-language", ""},
            {"accept-ranges", ""},
            {"accept", ""},
            {"access-control-allow-origin", ""},
            {"age", ""},
            {"allow", ""},
            {"authorization", ""},
            {"cache-control", ""},
            {"content-disposition", ""},
            {"content-encoding", ""},
            {"content-language", ""},
            {"content-length", ""},
            {"content-location", ""},
            {"content-range", ""},
            {"content-type", ""},
            {"cookie", ""},
            {"date", ""},
            {"etag", ""},
            {"expect", ""},
            {"expires", ""},
            {"from", ""},
            {"host", ""},
            {"if-match", ""},
            {"if-modified-since", ""},
            {"if-none-match", ""},
            {"if-range", ""},
            {"if-unmodified-since", ""},
            {"last-modified", ""},
            {"link", ""},
            {"location", ""},
            {"max-forwards", ""},
            {"proxy-authenticate", ""},
            {"proxy-authorization", ""},
            {"range", ""},
            {"referer", ""},
            {"refresh", ""},
            {"retry-after", ""},
            {"server", ""},
            {"set-cookie", ""},
            {"strict-transport-security", ""},
            {"transfer-encoding", ""},
            {"user-agent", ""},
            {"vary", ""},
            {"via", ""},
            {"www-authenticate", ""}
    };

    private static final Map<String, Integer> FULL = new HashMap<>();
    private static final Map<String, Integer> NAME = new HashMap<>();

    static {
        for (int i = 0; i < TABLE.length; i++) {
            int index = i + 1;
            String n = TABLE[i][0];
            String v = TABLE[i][1];

            if (!v.isEmpty()) {
                FULL.put(n + "\0" + v, index);
            }
            NAME.putIfAbsent(n, index);
        }
    }

    public static Integer full(String name, String value) {
        return FULL.get(name + "\0" + value);
    }

    public static Integer name(String name) {
        return NAME.get(name);
    }
}
