package org.example.connection;

import java.nio.ByteBuffer;

public interface Connection {

    /**
     * 异步写数据
     */
    void write(ByteBuffer buffer);

    /**
     * 注册读回调（有数据就通知）
     */
    void onRead(ReadHandler handler);

    /**
     * 关闭连接
     */
    void close();
}
