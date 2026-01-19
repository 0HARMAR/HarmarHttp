package org.example.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.nio.ByteBuffer;

public class NettyConnection implements Connection {

    private final Channel channel;
    private ReadHandler readHandler;

    public NettyConnection(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void onRead(ReadHandler handler) {
        this.readHandler = handler;
    }

    void fireRead(ByteBuf msg) {
        ByteBuffer buffer = msg.nioBuffer();
        readHandler.onRead(buffer);
    }

    @Override
    public void write(ByteBuffer buffer) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(buffer));
    }

    @Override
    public void close() {
        channel.close();
    }
}
