package org.example;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Router {
    private final Map<String, Map<String, RouteHandler>> routes = new HashMap<>();

    private final Map<String, Pattern> pathPatterns = new HashMap<>();

    private final List<RouterEntry> routerEntries = new ArrayList<>();

    // route handle function interface
    public interface RouteHandler {
        void handle(HttpRequest request, OutputStream output, Map<String,String> pathParams) throws IOException;
    }

    // route entry
    private static class RouterEntry {
        final String method;
        final String pathPattern;
        final RouteHandler handler;
        final int priority;

        RouterEntry(String method, String pathPattern, RouteHandler handler, int priority) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            this.priority = priority;
        }
    }

    public void register(String method, String pathPattern, RouteHandler handler) {
        register(method, pathPattern, handler, 10);
    }
    // register route
    public void register(String method, String pathPattern, RouteHandler handler, int priority) {
        method = method.toUpperCase();
        routes.computeIfAbsent(method, k -> new LinkedHashMap<>()).put(pathPattern, handler);
        routerEntries.add(new RouterEntry(method, pathPattern, handler, priority));

        if (pathPattern.contains("{")) {
            String regex = pathPattern
                    .replace(".", "\\.")
                    .replace("{", "(?<")
                    .replace("}", ">[^/]+");
            pathPatterns.put(regex, Pattern.compile("^" + regex + "$"));
        }

        routerEntries.sort(Comparator.comparingInt(e -> e.priority));
    }

    public void get(String pathPattern, RouteHandler handler) {
        register("GET", pathPattern, handler);
    }

    public void post(String pathPattern, RouteHandler handler) {
        register("POST", pathPattern, handler);
    }

    public RouteMatch findMatch(String method, String path) {
        method = method.toUpperCase();
        Map<String, RouteHandler> methodRoutes = routes.get(method);
        if (methodRoutes == null) return null;

        // 1. check match
        if (methodRoutes.containsKey(path)) {
            return new RouteMatch(methodRoutes.get(path),Collections.emptyMap());
        }

        // 2. check params path match
        for (RouterEntry entry : routerEntries) {
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
                    } catch (IllegalArgumentException e) {

                    }
                    return new RouteMatch(entry.handler, params);
                }
            }
        }
        return null;
    }

    public static class RouteMatch {
        public final RouteHandler handler;
        public final Map<String, String> pathParams;

        public RouteMatch(RouteHandler handler, Map<String, String> pathParams) {
            this.handler = handler;
            this.pathParams = pathParams;
        }
    }


}
