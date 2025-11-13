package org.example.monitor;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceMonitor {
    // request count
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    // response time count
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong maxResponseTime = new AtomicLong(0);
    private final AtomicLong minRequestTime = new AtomicLong(Long.MAX_VALUE);

    // concurrent count
    private final AtomicInteger currentConnections = new AtomicInteger(0);
    private final AtomicInteger maxConcurrentConnections = new AtomicInteger(0);

    // status code count
    private final Map<Integer, AtomicLong> statusCodeCounts = new ConcurrentHashMap<>();

    // error count
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();

    // start time
    private final long startTime = System.currentTimeMillis();

    // record request start time
    public void recordRequestStart() {
        totalRequests.incrementAndGet();
        int current = currentConnections.incrementAndGet();

        // update max concurrent connections
        while (true) {
            int max = maxConcurrentConnections.get();
            if (current <= max || maxConcurrentConnections.compareAndSet(max, current)) {
                break;
            }
        }
    }

    // record request complete
    public void recordRequestComplete(Long responseTime, int statusCode) {
        // record response time
        totalResponseTime.addAndGet(responseTime);

        // update max response time
        while (true) {
            Long max = maxResponseTime.get();
            if (responseTime <= max || maxResponseTime.compareAndSet(max, responseTime)) {
                break;
            }
        }

        // update min response time
        while (true) {
            Long min = minRequestTime.get();
            if (responseTime >= min || minRequestTime.compareAndSet(min, responseTime)) {
                break;
            }
        }

        statusCodeCounts.computeIfAbsent(statusCode, k -> new AtomicLong(0)).incrementAndGet();

        // update success / failed count
        if (statusCode < 400) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }

        // reduce current connections
        currentConnections.decrementAndGet();
    }

    // record errors
    public void recordError(String errorType) {
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        failedRequests.incrementAndGet();
        currentConnections.decrementAndGet();
    }

    // get monitor data
    public MonitorData getMonitorData() {
        long total =  totalRequests.get();
        long successful =  successfulRequests.get();
        long failed = failedRequests.get();
        long totalTime =  totalResponseTime.get();

        return new MonitorData(
                total,
                successful,
                failed,
                total > 0 ? totalTime / total : 0,
                maxResponseTime.get(),
                minRequestTime.get() == Long.MAX_VALUE ? 0 : minRequestTime.get(),
                (long) currentConnections.get(),
                maxConcurrentConnections.get(),
                (int) (System.currentTimeMillis() - startTime),
                convertAtomicToMap(statusCodeCounts),
                convertAtomicToMap(errorCounts)
        );
    }

    public <K> Map<K, Long> convertAtomicToMap(Map<K, AtomicLong> map) {
        Map<K, Long> result = new HashMap<>();
        for (Map.Entry<K, AtomicLong> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        totalResponseTime.set(0);
        maxResponseTime.set(0);
        minRequestTime.set(Long.MAX_VALUE);
        currentConnections.set(0);
        maxConcurrentConnections.set(0);
        statusCodeCounts.clear();
        errorCounts.clear();
    }
}
