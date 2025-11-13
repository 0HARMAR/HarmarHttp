package org.example.monitor;

import java.util.Map;

public class MonitorData {
    public final long totalRequests;
    public final long successfulRequests;
    public final long failedRequests;
    public final long averageResponseTime;
    public final long maxResponseTime;
    public final long minResponseTime;
    public final int currentConnections;
    public final int maxConcurrentConnections;
    public final long uptime;
    public final Map<Integer, Long> statusCodeCounts;
    public final Map<String, Long> errorCounts;

    public MonitorData(long totalRequests, long successfulRequests, long failedRequests,
                       long averageResponseTime, long maxResponseTime, long minResponseTime, long uptime,
                       int currentConnections, int maxConcurrentConnections,
                       Map<Integer, Long> statusCodeCounts, Map<String, Long> errorCounts) {
        this.totalRequests = totalRequests;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.averageResponseTime = averageResponseTime;
        this.maxResponseTime = maxResponseTime;
        this.minResponseTime = minResponseTime;
        this.statusCodeCounts = statusCodeCounts;
        this.errorCounts = errorCounts;
        this.uptime = uptime;
        this.currentConnections = currentConnections;
        this.maxConcurrentConnections = maxConcurrentConnections;
    }

    public double getSuccessRate() {
        return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0;
    }

    public double getErrorRate() {
        return totalRequests > 0 ? (double) failedRequests / totalRequests * 100 : 0;
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"totalRequests\":").append(totalRequests).append(",");
        json.append("\"successfulRequests\":").append(successfulRequests).append(",");
        json.append("\"failedRequests\":").append(failedRequests).append(",");
        json.append("\"successRate\":").append(String.format("%.2f", getSuccessRate())).append(",");
        json.append("\"errorRate\":").append(String.format("%.2f", getErrorRate())).append(",");
        json.append("\"averageResponseTime\":").append(averageResponseTime).append(",");
        json.append("\"maxResponseTime\":").append(maxResponseTime).append(",");
        json.append("\"minResponseTime\":").append(minResponseTime).append(",");
        json.append("\"currentConnections\":").append(currentConnections).append(",");
        json.append("\"maxConcurrentConnections\":").append(maxConcurrentConnections).append(",");
        json.append("\"uptime\":").append(uptime).append(",");
        json.append("\"statusCodeCounts\":").append(statusCodeCounts.toString()).append(",");
        json.append("\"errorCounts\":").append(errorCounts.toString());
        json.append("}");
        return json.toString();
    }
}
