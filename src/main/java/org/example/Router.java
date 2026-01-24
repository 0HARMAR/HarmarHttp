package org.example;

import org.example.http2.Frame;
import org.example.http2.HpackDynamicTable;
import org.example.http2.Http2Stream;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Router {

    // HTTP/1 路由 Map
    private final Map<String, Map<String, Http1RouteHandler>> http1Routes = new HashMap<>();
    private final Map<String, Pattern> pathPatterns = new HashMap<>();
    private final List<RouterEntry<Http1RouteHandler>> http1Entries = new ArrayList<>();

    // HTTP/2 路由 Map
    private final Map<String, Map<String, Http2RouteHandler>> http2Routes = new HashMap<>();
    private final List<RouterEntry<Http2RouteHandler>> http2Entries = new ArrayList<>();

    // -------------------- 接口 --------------------
    public interface Http1RouteHandler {
        void handle(HttpRequest request, HttpResponse response, Map<String,String> pathParams) throws IOException;
    }

    public interface Http2RouteHandler {
        void handle(HttpRequest request, Http2Stream stream, Map<String,String> pathParams,
                    HpackDynamicTable hpackDynamicTable, int streamId) throws IOException;
    }

    // -------------------- 路由条目 --------------------
    private static class RouterEntry<T> {
        final String method;
        final String pathPattern;
        final T handler;
        final int priority;

        RouterEntry(String method, String pathPattern, T handler, int priority) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            this.priority = priority;
        }
    }

    // -------------------- 注册 HTTP/1 路由 --------------------
    public void registerHttp1(String method, String pathPattern, Http1RouteHandler handler) {
        registerHttp1(method, pathPattern, handler, 10);
    }

    public void registerHttp1(String method, String pathPattern, Http1RouteHandler handler, int priority) {
        method = method.toUpperCase();
        http1Routes.computeIfAbsent(method, k -> new LinkedHashMap<>()).put(pathPattern, handler);
        http1Entries.add(new RouterEntry<>(method, pathPattern, handler, priority));

        if (pathPattern.contains("{")) {
            String regex = pathPattern
                    .replace(".", "\\.")
                    .replace("{", "(?<")
                    .replace("}", ">[^/]+)");
            pathPatterns.put(pathPattern, Pattern.compile("^" + regex + "$"));
        }

        http1Entries.sort(Comparator.comparingInt(e -> e.priority));
    }

    public void get(String pathPattern, Http1RouteHandler handler) {
        registerHttp1("GET", pathPattern, handler);
    }

    public void post(String pathPattern, Http1RouteHandler handler) {
        registerHttp1("POST", pathPattern, handler);
    }

    // -------------------- 注册 HTTP/2 路由 --------------------
    public void registerHttp2(String method, String pathPattern, Http2RouteHandler handler) {
        registerHttp2(method, pathPattern, handler, 10);
    }

    public void registerHttp2(String method, String pathPattern, Http2RouteHandler handler, int priority) {
        method = method.toUpperCase();
        http2Routes.computeIfAbsent(method, k -> new LinkedHashMap<>()).put(pathPattern, handler);
        http2Entries.add(new RouterEntry<>(method, pathPattern, handler, priority));

        if (pathPattern.contains("{")) {
            String regex = pathPattern
                    .replace(".", "\\.")
                    .replace("{", "(?<")
                    .replace("}", ">[^/]+)");
            pathPatterns.put(pathPattern, Pattern.compile("^" + regex + "$"));
        }

        http2Entries.sort(Comparator.comparingInt(e -> e.priority));
    }

    // -------------------- 匹配 HTTP/1 --------------------
    public RouteMatchHttp1 findMatchHttp1(String method, String path) {
        method = method.toUpperCase();
        Map<String, Http1RouteHandler> methodRoutes = http1Routes.get(method);
        if (methodRoutes == null) return null;

        if (methodRoutes.containsKey(path)) {
            return new RouteMatchHttp1(methodRoutes.get(path), Collections.emptyMap());
        }

        for (RouterEntry<Http1RouteHandler> entry : http1Entries) {
            if (!entry.method.equals(method)) continue;

            Pattern pattern = pathPatterns.get(entry.pathPattern);
            if (pattern != null) {
                Matcher matcher = pattern.matcher(path);
                if (matcher.matches()) {
                    Map<String, String> params = new HashMap<>();
                    try {
                        for (String name : matcher.namedGroups().keySet()) {
                            params.put(name, matcher.group(name));
                        }
                    } catch (IllegalArgumentException ignored) {}
                    return new RouteMatchHttp1(entry.handler, params);
                }
            }
        }
        return null;
    }

    // -------------------- 匹配 HTTP/2 --------------------
    public RouteMatchHttp2 findMatchHttp2(String method, String path) {
        method = method.toUpperCase();
        Map<String, Http2RouteHandler> methodRoutes = http2Routes.get(method);
        if (methodRoutes == null) return null;

        if (methodRoutes.containsKey(path)) {
            return new RouteMatchHttp2(methodRoutes.get(path), Collections.emptyMap());
        }

        for (RouterEntry<Http2RouteHandler> entry : http2Entries) {
            if (!entry.method.equals(method)) continue;

            Pattern pattern = pathPatterns.get(entry.pathPattern);
            if (pattern != null) {
                Matcher matcher = pattern.matcher(path);
                if (matcher.matches()) {
                    Map<String, String> params = new HashMap<>();
                    try {
                        for (String name : matcher.namedGroups().keySet()) {
                            params.put(name, matcher.group(name));
                        }
                    } catch (IllegalArgumentException ignored) {}
                    return new RouteMatchHttp2(entry.handler, params);
                }
            }
        }
        return null;
    }

    // -------------------- 返回结果 --------------------
    public static class RouteMatchHttp1 {
        public final Http1RouteHandler handler;
        public final Map<String, String> pathParams;

        public RouteMatchHttp1(Http1RouteHandler handler, Map<String, String> pathParams) {
            this.handler = handler;
            this.pathParams = pathParams;
        }
    }

    public static class RouteMatchHttp2 {
        public final Http2RouteHandler handler;
        public final Map<String, String> pathParams;

        public RouteMatchHttp2(Http2RouteHandler handler, Map<String, String> pathParams) {
            this.handler = handler;
            this.pathParams = pathParams;
        }
    }
}
