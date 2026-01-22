package org.example.http2;

public class SchedulerUnit {
    byte[] data;
    int streamId;

    public SchedulerUnit(byte[] data, int streamId) {
        this.data = data;
        this.streamId = streamId;
    }
}
