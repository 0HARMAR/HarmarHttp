package org.example;

import org.example.monitor.RequestMonitorContext;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

// 新增：客户端上下文
class ConnectionContext {
    final AsynchronousSocketChannel client;
    final ByteBuffer buffer = ByteBuffer.allocate(8192);
    final RequestMonitorContext monitor = new RequestMonitorContext();
    Protocol protocol = null;

    ConnectionContext(AsynchronousSocketChannel client) {
        this.client = client;
    }
}
