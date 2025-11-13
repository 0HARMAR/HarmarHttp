package org.example.security;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class DosDefender {
    // ip -> request timestamp
    private final ConcurrentMap<String, RequestRecord> ipRequestRecords = new ConcurrentHashMap<>();

    // time window size(millisecond),default 1 minute
    private final long timeWindowMillis;

    // time window allowed max request
    private final int maxRequestPerWindow;

    // ip record out of date time(millisecond), default 5 minute
    private final long ipExpirationMillis;

    // scheduler that clean out of date ip at regular interval
    private final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1);

    // cleaner interval
    private static final long CLEANER_INTERVAL = 30_000;

    // synchronize clean lock
    private final ReentrantLock cleanupLock = new ReentrantLock();

    public DosDefender(long timeWindowMillis, int maxRequestPerWindow, long ipExpirationMillis) {
        this.timeWindowMillis = timeWindowMillis;
        this.maxRequestPerWindow = maxRequestPerWindow;
        this.ipExpirationMillis = ipExpirationMillis;

        // start regular clean tast
        cleaner.scheduleAtFixedRate(this::cleanupExpiredIps,
                CLEANER_INTERVAL,CLEANER_INTERVAL, TimeUnit.MICROSECONDS);
    }

    /**
     * check whether allow request from an ip
     * @param ipAddress client ip
     * @return true if request allowed, false repr out limit
     */
    public boolean allowRequest(String ipAddress) {
        long currentTime = System.currentTimeMillis();

        // get or create this ip record
        RequestRecord record = ipRequestRecords.computeIfAbsent(ipAddress, k -> new RequestRecord(currentTime));
        return record.allowRequest(currentTime,timeWindowMillis,maxRequestPerWindow);
    }

    /**
     * clean out of date ip record
     */
    private void cleanupExpiredIps() {
        if (!cleanupLock.tryLock()) {return;}

        try {
            long currentTime = System.currentTimeMillis();
            ipRequestRecords.entrySet().removeIf(entry ->
                    currentTime - entry.getValue().getLastAccessTime() > ipExpirationMillis);
        } finally {
            cleanupLock.unlock();
        }
    }

    /**
     * stop clean tast
     */
    public void shutdown() {
        cleaner.shutdown();
        try {
            if (!cleaner.awaitTermination(5, TimeUnit.SECONDS)) {
                cleaner.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleaner.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    private static class RequestRecord {
        private final long[] requestTimes;

        private volatile int cursor = 0;

        private final int bufferSize;

        private volatile long lastAccessTime;

        private final Object lock = new Object();

        RequestRecord(long initialTime) {
            this.bufferSize = 100;
            this.requestTimes = new long[bufferSize];
            this.lastAccessTime = initialTime;
        }

        boolean allowRequest(long currentTime, long windowMillis,int maxRequests) {
            synchronized (lock) {
                lastAccessTime = currentTime;

                // clean out of date request (sliding window)
                int validCount = 0;
                for (int i = 0; i < bufferSize; i++) {
                    if (currentTime - requestTimes[i] <= windowMillis) {
                        validCount++;
                    }
                }

                // if valid request to max,reject
                if (validCount >= maxRequests) {
                    return false;
                }

                // record new request
                requestTimes[cursor] = currentTime;
                cursor = (cursor + 1) % bufferSize;
                return true;
            }
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }
    }
}
