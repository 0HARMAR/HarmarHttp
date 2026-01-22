package org.example.monitor;

import org.example.HarmarHttpServer;
import org.example.HttpResponse;
import org.example.HttpStatus;
import org.example.ResponseBody;

import java.nio.charset.StandardCharsets;

public class MonitorEndpoints {
    private final PerformanceMonitor monitor;

    public MonitorEndpoints(PerformanceMonitor monitor) {
        this.monitor = monitor;
    }

    public void registerEndPoints(HarmarHttpServer server) {
        // health check point
        server.registerRouteHttp1("GET", "/health", (request, response, pathParams) -> {
            MonitorData data = monitor.getMonitorData();
            boolean isHealthy = data.getErrorRate() < 5.0 && data.currentConnections < 100;

            String json = String.format(
                    "{\"status\":\"%s\",\"errorRate\":%.2f,\"currentConnections\":%d}",
                    isHealthy ? "healthy" : "unhealthy",
                    data.getErrorRate(),
                    data.currentConnections
            );

            response.setStatus(isHealthy ?  HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "application/json");
            response.setHeader("Content-Length", String.valueOf(json.length()));

            ResponseBody body = new ResponseBody();
            body.addChunk(json.getBytes(StandardCharsets.UTF_8));
            body.end();
            response.setBody(body);
        });

        // performance index
        server.registerRouteHttp1("GET", "/metrics", (request, response, pathParams) -> {
            MonitorData data = monitor.getMonitorData();
            response.setStatus(HttpStatus.OK);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "application/json");
            response.setHeader("Content-Length", String.valueOf(data.toJson().length()));

            ResponseBody  body = new ResponseBody();
            body.addChunk(data.toJson().getBytes(StandardCharsets.UTF_8));
            body.end();
            response.setBody(body);
        });

        // simple count index
        server.registerRouteHttp1("GET", "/stats", (request, response, pathParams) -> {
            MonitorData data = monitor.getMonitorData();
            String html = generateStatsHtml(data);

            response.setStatus(HttpStatus.OK);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "text/html; charset=UTF-8");
            response.setHeader("Content-Length", String.valueOf(html.getBytes(StandardCharsets.UTF_8).length));

            ResponseBody body = new ResponseBody();
            body.addChunk(html.getBytes(StandardCharsets.UTF_8));
            body.end();
            response.setBody(body);
        });

        // reset count index
        server.registerRouteHttp1("POST", "/reset", (request, response, pathParams) -> {
            monitor.reset();
            String json = "{\"message\":\"Statistics reset successfully\"}";

            response.setStatus(HttpStatus.OK);
            response.setDefaultHeaders();
            response.setHeader("Content-Type", "application/json; charset=UTF-8");
            response.setHeader("Content-Length", String.valueOf(json.getBytes(StandardCharsets.UTF_8).length));

            ResponseBody body = new ResponseBody();
            body.addChunk(json.getBytes(StandardCharsets.UTF_8));
            body.end();
            response.setBody(body);
        });

    }

    private String generateStatsHtml(MonitorData data) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>HarmarHttp Monitor</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .metric-card { 
                        border: 1px solid #ddd; 
                        padding: 15px; 
                        margin: 10px; 
                        border-radius: 5px; 
                        display: inline-block;
                        width: 200px;
                    }
                    .metric-value { 
                        font-size: 24px; 
                        font-weight: bold; 
                        color: #007bff; 
                    }
                    .metric-label { 
                        color: #666; 
                        font-size: 14px; 
                    }
                    .healthy { color: #28a745; }
                    .warning { color: #ffc107; }
                    .danger { color: #dc3545; }
                </style>
            </head>
            <body>
                <h1>HarmarHttp Performance Monitor</h1>
                
                <div class="metric-card">
                    <div class="metric-value">%d</div>
                    <div class="metric-label">Total Requests</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-value %s">%.2f%%</div>
                    <div class="metric-label">Success Rate</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-value">%dms</div>
                    <div class="metric-label">Avg Response Time</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-value">%d</div>
                    <div class="metric-label">Current Connections</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-value">%d</div>
                    <div class="metric-label">Max Concurrent</div>
                </div>
                
                <div class="metric-card">
                    <div class="metric-value">%ds</div>
                    <div class="metric-label">Uptime</div>
                </div>
                
                <h2>Status Code Distribution</h2>
                <pre>%s</pre>
                
                <h2>Error Distribution</h2>
                <pre>%s</pre>
                
                <script>
                    // 自动刷新页面
                    setTimeout(() => {
                        location.reload();
                    }, 5000);
                </script>
            </body>
            </html>
            """,
                data.totalRequests,
                data.getSuccessRate() > 95 ? "healthy" : data.getSuccessRate() > 90 ? "warning" : "danger",
                data.getSuccessRate(),
                data.averageResponseTime,
                data.currentConnections,
                data.maxConcurrentConnections,
                data.uptime / 1000,
                data.statusCodeCounts.toString(),
                data.errorCounts.toString()
        );
    }
}
