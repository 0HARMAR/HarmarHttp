package org.example.http2;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Scheduler {

    private final Map<Integer, Queue<SchedulerUnit>> streamQueues = new ConcurrentHashMap<>();
    private final List<Integer> activeStreams = new ArrayList<>();

    private int rrIndex = 0;
    private final int MAX_DATA_SIZE = 4 * 1024;

    private ChannelHandlerContext ctx;
    private boolean scheduling = false; // 防止递归重入

    /* ===================== 外部入口 ===================== */

    public void addSchedulerUnit(Frame data, int streamId) {
        splitAndEnqueue(data, streamId);

        // ❗ 状态变化 → 立刻尝试调度
        if (ctx != null) {
            trySchedule();
        }
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    /* ===================== 调度核心 ===================== */

    private void trySchedule() {
        if (scheduling) return;

        SchedulerUnit unit = nextUnit();
        if (unit == null) {
            return;
        }

        scheduling = true;

        ctx.writeAndFlush(Unpooled.wrappedBuffer(unit.data))
                .addListener(f -> {
                    scheduling = false;
                    if (f.isSuccess()) {
                        System.out.println("发送 DATA" + unit.streamId);
                        trySchedule();
                    }
                });
    }

    private SchedulerUnit nextUnit() {
        if (activeStreams.isEmpty()) return null;

        int size = activeStreams.size();
        rrIndex %= size;

        int streamId = activeStreams.get(rrIndex);
        Queue<SchedulerUnit> q = streamQueues.get(streamId);

        SchedulerUnit unit = q.poll();

        if (unit == null) {
            // 该 stream 发完了，移除
            activeStreams.remove(rrIndex);
            streamQueues.remove(streamId);
            return nextUnit();
        }

        rrIndex++;
        return unit;
    }

    /* ===================== 拆包逻辑 ===================== */

    private void splitAndEnqueue(Frame data, int streamId) {
        byte[] payload = data.getPayload();
        FrameHeader header = data.getHeader();

        int offset = 0;
        while (offset < payload.length) {
            int len = Math.min(MAX_DATA_SIZE, payload.length - offset);
            byte[] chunk = Arrays.copyOfRange(payload, offset, offset + len);

            EnumSet<FrameFlag> flags = EnumSet.noneOf(FrameFlag.class);
            if (header.FrameFlags != null) flags.addAll(header.FrameFlags);
            if (offset + len < payload.length) {
                flags.remove(FrameFlag.END_STREAM);
            }

            FrameHeader subHeader =
                    new FrameHeader(len, header.FrameType, flags, header.StreamID);
            Frame subFrame = new Frame(subHeader, chunk);

            streamQueues
                    .computeIfAbsent(streamId, k -> {
                        activeStreams.add(k);
                        return new ConcurrentLinkedQueue<>();
                    })
                    .add(new SchedulerUnit(subFrame.toBytes(), streamId));

            offset += len;
        }
    }
}
