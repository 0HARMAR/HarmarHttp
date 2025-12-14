package org.example.monitor;

import org.example.HarmarHttpServer;
import org.example.HttpResponse;

import java.nio.charset.StandardCharsets;

public class MonitorEndpoints {
    private final PerformanceMonitor monitor;

    public MonitorEndpoints(PerformanceMonitor monitor) {
        this.monitor = monitor;
    }

    public void registerEndPoints(HarmarHttpServer server) {
        // health check point
        server.registerRoute("GET", "/health", ((request, response, pathParams) -> {
            MonitorData data = monitor.getMonitorData();
            boolean isHealthy = data.getErrorRate() < 5.0 && data.currentConnections < 100;

            String json = String.format(
                    "{\"status\":\"%s\",\"errorRate\":%.2f,\"currentConnections\":%d}",
                    isHealthy ? "healthy" : "unhealthy",
                    data.getErrorRate(),
                    data.currentConnections
            );

            server.sendResponse(response.getByteArrayOutputStream(), isHealthy ? HttpResponse.HttpStatus.OK.code
                    : HttpResponse.HttpStatus.SERVICE_UNAVAILABLE.code, isHealthy ?
                    HttpResponse.HttpStatus.OK.message : HttpResponse.HttpStatus.SERVICE_UNAVAILABLE.message,
                    "application/json",json.getBytes(StandardCharsets.UTF_8) );
        }));

        // performance index
        server.registerRoute("GET", "/metrics", ((request, response, pathParams) -> {
            MonitorData data = monitor.getMonitorData();
            server.sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message,
                    "application/json", data.toJson().getBytes(StandardCharsets.UTF_8));
        }));

        // simple count index
        server.registerRoute("GET", "/stats", ((request, response, pathParams) -> {
            MonitorData data = monitor.getMonitorData();
            String html = generateStatsHtml(data);
            server.sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message,
                    "text/html", html.getBytes(StandardCharsets.UTF_8) );
        }));

        // reset count index
        server.registerRoute("POST", "/reset", ((request, response, pathParams) -> {
            monitor.reset();
            server.sendResponse(response.getByteArrayOutputStream(), HttpResponse.HttpStatus.OK.code, HttpResponse.HttpStatus.OK.message,
                    "application/json", "{\"message\":\"Statistics reset successfully\"}".getBytes(StandardCharsets.UTF_8));
        }));
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
