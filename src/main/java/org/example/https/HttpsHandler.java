package org.example.https;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.example.HttpRequest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.example.HttpResponse;
import org.example.HttpVersion;
import org.example.Router;
import org.example.protocol.HttpRequestParser;

public class HttpsHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final Router router;
    private ByteArrayOutputStream cumulation = new ByteArrayOutputStream();


    public HttpsHandler(Router router) {
        this.router = router;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);
        try {
            cumulation.write(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        byte[] all = cumulation.toByteArray();
        int requestEnd = findHttpRequestEnd(ByteBuffer.wrap(all));
        if (requestEnd == -1) {
            return;
        }

        byte[] requestData = Arrays.copyOfRange(all, 0, requestEnd);

        String requestStr = new String(requestData);
        System.out.println("Received HTTP request:\n" + requestStr);

        HttpRequest request = null;
        try {
            request = HttpRequestParser.parseRequest(new BufferedReader(new StringReader(requestStr)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Router.RouteMatch match = router.findMatch(request.method, request.path);
        HttpResponse response = new HttpResponse();
        boolean keepAlive = true;
        if (match != null) {
            try {
                response.setHttpVersion(request.protocol.startsWith("HTTP/1.1") ? HttpVersion.HTTP_1_1 : HttpVersion.HTTP_1_0);
                keepAlive = request.headers.getOrDefault("connection", "").equalsIgnoreCase("keep-alive");
                if (!keepAlive) {
                    response.setHeader("Connection", "close");
                } else {
                    response.setHeader("Connection", "keep-alive");
                }

                match.handler.handle(request, response, match.pathParams);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // write response
        ByteBuf out = ctx.alloc().buffer();
        out.writeBytes(response.toBytes());
        boolean finalKeepAlive = keepAlive;
        ctx.writeAndFlush(out).addListener(finalKeepAlive ? f -> {} : ChannelFutureListener.CLOSE);

        cumulation.reset();
        cumulation.write(all, requestEnd, all.length - requestEnd);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * 找到完整 HTTP/1 请求结束位置（按 \r\n\r\n 或 Content-Length）
     * 返回 -1 表示请求未完整
     */
    private int findHttpRequestEnd(ByteBuffer buf) {
        int startPos = buf.position();
        int limit = buf.limit();

        // 简单方式：找 \r\n\r\n
        for (int i = startPos; i < limit - 3; i++) {
            if (buf.get(i) == '\r' && buf.get(i + 1) == '\n'
                    && buf.get(i + 2) == '\r' && buf.get(i + 3) == '\n') {
                // 包含 header 长度
                int headersEnd = i + 4;

                // 检查是否有 Content-Length
                int contentLength = getContentLength(buf, startPos, headersEnd);
                if (contentLength > 0) {
                    // 确保 body 也完整
                    if (limit - headersEnd >= contentLength) {
                        return headersEnd + contentLength - startPos;
                    } else {
                        return -1; // body 未完整
                    }
                } else {
                    // 无 body，完整请求
                    return headersEnd - startPos;
                }
            }
        }

        return -1; // header 未完整
    }

    /**
     * 从 buffer 的 [start, end) 区间解析 Content-Length
     */
    private int getContentLength(ByteBuffer buf, int start, int end) {
        byte[] headerBytes = new byte[end - start];
        int oldPos = buf.position();
        buf.position(start);
        buf.get(headerBytes);
        buf.position(oldPos);

        String headers = new String(headerBytes);
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.split(":")[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }
}

