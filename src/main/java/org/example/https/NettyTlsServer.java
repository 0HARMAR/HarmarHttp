package org.example.https;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.example.Router;
import org.example.http2.*;

import javax.net.ssl.SSLException;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class NettyTlsServer {

    private final int port;
    private final SslContext sslContext;
    private final Router router;

    public NettyTlsServer(int port, Router router) {
        this.port = port;
        // 获取 resources 目录下的证书文件路径
        File certFile = null;
        try {
            certFile = new File(getClass().getClassLoader().getResource("server.crt").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        File keyFile = null;
        try {
            keyFile = new File(getClass().getClassLoader().getResource("server.key").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try {
            this.sslContext = SslContextBuilder
                    .forServer(certFile, keyFile)
                    .applicationProtocolConfig(
                            new ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2,
                                    ApplicationProtocolNames.HTTP_1_1
                            )
                    )
                    .build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
        this.router = router;
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();

                            // 1. 添加 TLS 支持
                            p.addLast("ssl", sslContext.newHandler(ch.alloc()));

                            // 2. 添加 ALPN 协商处理器
                            p.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                                @Override
                                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                                    System.out.println("ALPN协商的协议: " + protocol);

                                    // 3. 根据ALPN协商结果添加不同的处理器
                                    if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                        // HTTP/2 协议 - 添加HTTP/2特定处理器
                                        configureForHttp2(ctx);
                                    } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                                        // HTTP/1.1 协议 - 添加HTTP/1.1特定处理器
                                        configureForHttp11(ctx);
                                    } else {
                                        // 其他协议 - 使用原始数据处理器
                                    }
                                }

                                private void configureForHttp2(ChannelHandlerContext ctx) {
                                    System.out.println("配置为HTTP/2协议处理");
                                    Http2Manager http2Manager = new Http2Manager();

                                    // 简单处理：直接添加一个处理器来读取原始数据
                                    ctx.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                        private byte[] cumulation = new byte[0];
                                        boolean sendPreface = false;
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                            // 读取原始字节数据
                                            byte[] data = new byte[msg.readableBytes()];
                                            msg.readBytes(data);

                                            ByteBuffer buffer = ByteBuffer.allocate(data.length + cumulation.length);
                                            buffer.put(cumulation);
                                            buffer.put(data);
                                            cumulation = buffer.array();

                                            // remove first 24 byte http2 prefence
                                            if (cumulation.length >= 3) {
                                                if (cumulation[0] == 'P'
                                                        && cumulation[1] == 'R' && cumulation[2] == 'I') {
                                                    if (cumulation.length >= 24) {
                                                        byte[] temp = cumulation;
                                                        cumulation = Arrays.copyOfRange(temp, 24, temp.length);
                                                    }
                                                }
                                            } else {
                                                return;
                                            }

                                            ByteBuffer receivedBuf =  ByteBuffer.wrap(cumulation);
                                            boolean haveFrame = http2Manager.decodeAndHandle(receivedBuf);
                                            if (!haveFrame) {
                                                return;
                                            }

                                            BlockingQueue<ByteBuffer> controlResponse = http2Manager.getControlFrameQueue();
                                            Map<Integer, Http2Stream> streams = http2Manager.getStreams();

                                            // first send controlResponse
                                            while (!controlResponse.isEmpty()) {
                                                ByteBuffer frame = controlResponse.poll();
                                                if (frame != null) {
                                                    ByteBuf byteBuf = Unpooled.wrappedBuffer(frame);
                                                    ChannelFuture future = ctx.writeAndFlush(byteBuf);
                                                    try {
                                                        future.sync(); // 等待此帧发送完成后再处理下一个
                                                    } catch (InterruptedException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            }

                                            // send response
                                            for (Map.Entry<Integer, Http2Stream> entry : streams.entrySet()) {
                                                Http2Stream stream = entry.getValue();
                                                while (!stream.getResponseQueue().isEmpty()) {
                                                    Frame frame = stream.getResponseQueue().poll();
                                                    ByteBuf byteBuf = Unpooled.wrappedBuffer(frame.toBytes());
                                                    ChannelFuture future = ctx.writeAndFlush(byteBuf);
                                                    try {
                                                        future.sync(); // 等待此帧发送完成后再处理下一个
                                                    } catch (InterruptedException e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                }
                                            }
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            cause.printStackTrace();
                                            ctx.close();
                                        }
                                    });
                                }

                                private void configureForHttp11(ChannelHandlerContext ctx) {
                                    System.out.println("配置为HTTP/1.1协议处理");

                                    ctx.pipeline().addLast("https", new HttpsHandler(router));
                                }
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Netty TLS服务器启动，监听端口: " + port);
            System.out.println("支持协议: HTTP/2, HTTP/1.1");
            System.out.println("使用自签名证书，客户端可能需要忽略证书警告");

            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}