package org.example.http2;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Http2Stream {
    private final int streamId;
    private final BlockingQueue<Frame> requestFrames = new LinkedBlockingQueue<>();
    private final BlockingQueue<Frame> responseFrames = new LinkedBlockingQueue<>();
    private StreamState state = StreamState.IDLE;

    public Http2Stream(int streamId) {
        this.streamId = streamId;
    }

    // ------------------- 基本字段访问 -------------------
    public int getStreamId() {
        return streamId;
    }

    public StreamState getState() {
        return state;
    }

    public BlockingQueue<Frame> getRequestQueue() {
        return requestFrames;
    }

    public BlockingQueue<Frame> getResponseQueue() {
        return responseFrames;
    }

    // ------------------- 接收帧事件 -------------------
    public synchronized void onRecvFrame(Frame frame) {
        if (state == StreamState.CLOSED) {
            System.out.println("⚠️ Stream " + streamId + " is closed, can't receive frame");
            return;
        }

        switch (frame.getHeader().FrameType) {
            case HEADERS -> handleRecvHeaders(frame);
            case DATA -> handleRecvData(frame);
            case RST_STREAM -> handleRecvRst();
            default -> {} // 其他帧暂时忽略
        }
    }

    private void handleRecvHeaders(Frame frame) {
        switch (state) {
            case IDLE -> state = StreamState.OPEN;
            case OPEN, HALF_CLOSED_LOCAL -> {} // 可能是 CONTINUATION
            default -> protocolError();
        }

        requestFrames.offer(frame);

        // 如果客户端设置了 END_STREAM
        if (frame.getHeader().FrameFlags.contains(FrameFlag.END_STREAM)) {
            if (state == StreamState.OPEN) state = StreamState.HALF_CLOSED_REMOTE;
            else if (state == StreamState.HALF_CLOSED_LOCAL) state = StreamState.CLOSED;
        }
    }

    private void handleRecvData(Frame frame) {
        switch (state) {
            case OPEN, HALF_CLOSED_LOCAL -> requestFrames.offer(frame);
            default -> protocolError();
        }

        if (frame.getHeader().FrameFlags.contains(FrameFlag.END_STREAM)) {
            if (state == StreamState.OPEN) state = StreamState.HALF_CLOSED_REMOTE;
            else if (state == StreamState.HALF_CLOSED_LOCAL) state = StreamState.CLOSED;
        }
    }

    private void handleRecvRst() {
        state = StreamState.CLOSED;
        requestFrames.clear();
        responseFrames.clear();
    }

    private void protocolError() {
        System.err.println("⚠️ Protocol error on stream " + streamId);
        state = StreamState.CLOSED;
        requestFrames.clear();
        responseFrames.clear();
    }

    // ------------------- 状态查询 -------------------
    public boolean isOpen() {
        return state == StreamState.OPEN;
    }

    public boolean isHalfClosedLocal() {
        return state == StreamState.HALF_CLOSED_LOCAL;
    }

    public boolean isHalfClosedRemote() {
        return state == StreamState.HALF_CLOSED_REMOTE;
    }

    public boolean isClosed() {
        return state == StreamState.CLOSED;
    }

    public synchronized void queueResponse(Frame frame) {
        if (state == StreamState.CLOSED) {
            System.out.println("⚠️ Stream " + streamId + " is closed, can't queue response");
        }
        // 放入响应队列
        responseFrames.offer(frame);

        // END_STREAM 自动更新状态
        if (frame.getHeader().FrameFlags.contains(FrameFlag.END_STREAM)) {
            if (state == StreamState.OPEN) state = StreamState.HALF_CLOSED_LOCAL;
            else if (state == StreamState.HALF_CLOSED_REMOTE) state = StreamState.CLOSED;
        }
    }


    // ------------------- 枚举 -------------------
    public enum StreamState {
        IDLE,
        OPEN,
        HALF_CLOSED_LOCAL,
        HALF_CLOSED_REMOTE,
        CLOSED
    }
}
