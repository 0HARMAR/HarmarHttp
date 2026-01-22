// Scheduler.java
package org.example.http2;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Scheduler {
    private final ConcurrentHashMap<Integer, Queue<SchedulerUnit>> streamQueues = new ConcurrentHashMap<>();
    private final List<Integer> activeStreams = Collections.synchronizedList(new ArrayList<>());
    private int currentIndex = 0;
    private final int MaxDataSize = 4 * 1024;

    public void addSchedulerUnit(Frame data, int streamId) {
        byte[] payload = data.getPayload();
        FrameHeader header = data.getHeader();
        int offset = 0;

        while (offset < payload.length) {
            // 本次分块大小
            int length = Math.min(MaxDataSize, payload.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(payload, offset, chunk, 0, length);

            // 复制 header 并设置新的 payload 长度
            EnumSet<FrameFlag> flags = EnumSet.noneOf(FrameFlag.class);
            if (header.FrameFlags != null) {
                flags.addAll(header.FrameFlags);
            }

            // 如果不是最后一个分块，去掉 END_STREAM 标记
            if (offset + length < payload.length) {
                flags.remove(FrameFlag.END_STREAM);
            }

            FrameHeader subHeader = new FrameHeader(length, header.FrameType, flags, header.StreamID);
            Frame subFrame = new Frame(subHeader, chunk);

            // 添加到对应流队列
            streamQueues.computeIfAbsent(streamId, id -> new ConcurrentLinkedQueue<>())
                    .add(new SchedulerUnit(subFrame.toBytes(), streamId));

            // 标记流为活跃
            if (!activeStreams.contains(streamId)) {
                synchronized (activeStreams) {
                    if (!activeStreams.contains(streamId)) activeStreams.add(streamId);
                }
            }

            offset += length;
        }
    }


    private SchedulerUnit getScheduleUnit() {
        if (activeStreams.isEmpty()) return null;
        int attempts = 0;
        while (attempts < activeStreams.size()) {
            int streamId = activeStreams.get(currentIndex);
            Queue<SchedulerUnit> queue = streamQueues.get(streamId);

            if (queue != null && !queue.isEmpty()) {
                SchedulerUnit unit = queue.poll();
                if (queue.isEmpty()) {
                    synchronized (activeStreams) {
                        streamQueues.remove(streamId);
                        activeStreams.remove(Integer.valueOf(streamId));
                        if (currentIndex >= activeStreams.size()) currentIndex = 0;
                    }
                } else {
                    currentIndex = (currentIndex + 1) % activeStreams.size();
                }
                return unit;
            } else {
                currentIndex = (currentIndex + 1) % activeStreams.size();
                attempts++;
            }
        }
        return null;
    }

    // 核心：在 EventLoop 内顺序发送 DATA
    public void schedule(ChannelHandlerContext ctx) {
        ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                SchedulerUnit unit;
                while ((unit = getScheduleUnit()) != null) {
                    ctx.write(Unpooled.wrappedBuffer(unit.data));
                }
                ctx.flush();

                // 如果还有数据，递归调度
                if (!streamQueues.isEmpty()) {
                    ctx.executor().execute(this);
                }
            }
        });
    }
}
