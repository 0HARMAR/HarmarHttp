package org.example.monitor;

public class RequestMonitorContext {
    final long startTime;

    public RequestMonitorContext() {
        startTime = System.currentTimeMillis();
    }

    public long getStartTime() {
        return startTime;
    }
}
