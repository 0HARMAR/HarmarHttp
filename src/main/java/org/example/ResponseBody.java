package org.example;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ResponseBody {
    private final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();
    private boolean ChunkedTransfer = false;
    private volatile boolean end = false;
    private volatile AtomicBoolean writing = new AtomicBoolean(false);

    private Runnable onDataAvailable;

    // only in no chunk transfer
    private boolean isBigFile = false;

    public void setOnDataAvailable(Runnable onDataAvailable) {
        this.onDataAvailable = onDataAvailable;
    }

    public void end() {
        end = true;
       if (ChunkedTransfer) {
           chunks.add(ByteBuffer.wrap("0\r\n\r\n".getBytes(UTF_8)));
       }
        if (onDataAvailable != null) {
            onDataAvailable.run();
        }
    }

    public void addChunk(byte[] data) {
        if (data.length > 1024 * 1024 * 4) {
            isBigFile = true;
        }

        if (data == null) {
            data = new byte[0];
        }

        if (ChunkedTransfer) {
            // 计算十六进制长度
            String hexLength = Integer.toHexString(data.length);
            String chunkHeader = hexLength + "\r\n";

            // 构建完整的chunk: header + data + \r\n
            byte[] headerBytes = chunkHeader.getBytes(UTF_8);
            byte[] trailerBytes = "\r\n".getBytes(UTF_8); // 每个chunk末尾也要有\r\n

            byte[] combined = new byte[headerBytes.length + data.length + trailerBytes.length];
            System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
            System.arraycopy(data, 0, combined, headerBytes.length, data.length);
            System.arraycopy(trailerBytes, 0, combined, headerBytes.length + data.length, trailerBytes.length);

            chunks.add(ByteBuffer.wrap(combined));
        }
        else {
            chunks.add(ByteBuffer.wrap(data));
        }
        if (onDataAvailable != null) {
            onDataAvailable.run();
        }
    }


    ByteBuffer poll() {
        return chunks.poll();
    }

    boolean isEnd() {
        return end;
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[0];
        for (ByteBuffer chunk : chunks) {
            byte[] chunkBytes = new byte[chunk.remaining()];
            chunk.get(chunkBytes);
            bytes = concat(bytes, chunkBytes);
        }

        return bytes;
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public boolean trySetWriting() {
        return writing.compareAndSet(false, true);
    }

    public void clearWriting() {
        writing.set(false);
    }

    public void setChunkedTransfer(boolean chunkedTransfer) {
        ChunkedTransfer = chunkedTransfer;
    }

    public boolean isChunkedTransfer() {
        return ChunkedTransfer;
    }

    public boolean isBigFile() {
        return isBigFile;
    }
}
