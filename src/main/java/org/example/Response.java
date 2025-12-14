package org.example;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Response {
    // for chunked transfer
    private final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();

    // for non-chunked transfer
    private ByteArrayOutputStream byteArrayOutputStream;

    // chunk mode response line and header
    byte[] responseLineAndHeader;

    // mode
    private boolean chunkedTransfer;

    private volatile boolean end = false;

    public void addChunk(byte[] data) {
        chunks.add(ByteBuffer.wrap(data));
    }

    public void end() {
        end = true;
    }

    ByteBuffer poll() {
        return chunks.poll();
    }

    boolean isEnd() {
        return end;
    }

    public ByteArrayOutputStream getByteArrayOutputStream() {
        return byteArrayOutputStream;
    }

    public void setChunkedTransfer() {
        this.chunkedTransfer = true;
    }

    public boolean getChunkedTransfer() {
        return this.chunkedTransfer;
    }

    public void setResponseLineAndHeader(byte[] responseLineAndHeader) {
        this.responseLineAndHeader = responseLineAndHeader;
    }

    public byte[] getResponseLineAndHeader() {
        return this.responseLineAndHeader;
    }

    public Response(OutputStream outputStream, boolean chunkedTransfer) {
        this.byteArrayOutputStream = (ByteArrayOutputStream) outputStream;
        this.chunkedTransfer = chunkedTransfer;
    }
}
