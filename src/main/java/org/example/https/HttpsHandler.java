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

import static org.example.protocol.HttpRequestParser.findHttpRequestEnd;

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

        Router.RouteMatchHttp1 match = router.findMatchHttp1(request.method, request.path);

        HttpResponse response = new HttpResponse();
        boolean keepAlive = true;

        if (match != null) {
            try {
                // 设置 HTTP 版本
                response.setHttpVersion(request.protocol.getName().startsWith("HTTP/1.1")
                        ? HttpVersion.HTTP_1_1
                        : HttpVersion.HTTP_1_0);

                // 处理 Connection 头
                keepAlive = "keep-alive".equalsIgnoreCase(request.headers.getOrDefault("connection", ""));
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
}

