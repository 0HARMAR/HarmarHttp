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
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NettyTlsServer {

    private final int port;
    private final SslContext sslContext;
    private final Router router;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            10, 10, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

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

                                // configureForHttp2
                                private void configureForHttp2(ChannelHandlerContext ctx) {
                                    System.out.println("配置为HTTP/2协议处理");
                                    Http2Manager http2Manager = new Http2Manager(router);
                                    Scheduler scheduler = new Scheduler();

                                    ctx.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                        private byte[] cumulation = new byte[0];
                                        private boolean schedulerStarted = false;

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                            byte[] data = new byte[msg.readableBytes()];
                                            msg.readBytes(data);

                                            ByteBuffer buffer = ByteBuffer.allocate(cumulation.length + data.length);
                                            buffer.put(cumulation);
                                            buffer.put(data);
                                            cumulation = buffer.array();

                                            if (cumulation.length >= 24 && cumulation[0] == 'P' && cumulation[1] == 'R') {
                                                cumulation = Arrays.copyOfRange(cumulation, 24, cumulation.length);
                                            } else if (cumulation.length < 3) {
                                                return;
                                            }

                                            ByteBuffer receivedBuf = ByteBuffer.wrap(cumulation);
                                            boolean haveFrame = http2Manager.decodeAndHandle(receivedBuf);
                                            if (!haveFrame) return;

                                            BlockingQueue<ByteBuffer> controlFrames = http2Manager.getControlFrameQueue();
                                            Map<Integer, Http2Stream> streams = http2Manager.getStreams();

                                            // 1️⃣ 发送控制帧
                                            ByteBuffer frame;
                                            while ((frame = controlFrames.poll()) != null) {
                                                ctx.write(Unpooled.wrappedBuffer(frame));
                                            }
                                            ctx.flush();

                                            // 2️⃣ 发送 HEADERS
                                            for (Http2Stream stream : streams.values()) {
                                                Queue<Frame> tempDataFrames = new LinkedList<>();
                                                while (!stream.getResponseQueue().isEmpty()) {
                                                    Frame f = stream.getResponseQueue().poll();
                                                    if (f.getHeader().FrameType == FrameType.HEADERS) {
                                                        ctx.write(Unpooled.wrappedBuffer(f.toBytes()));
                                                    } else {
                                                        tempDataFrames.add(f);
                                                    }
                                                }
                                                stream.getResponseQueue().addAll(tempDataFrames);
                                            }
                                            ctx.flush();

                                            // 3️⃣ 将 DATA 放入 Scheduler
                                            for (Http2Stream stream : streams.values()) {
                                                while (!stream.getResponseQueue().isEmpty()) {
                                                    Frame f = stream.getResponseQueue().poll();
                                                    if (f.getHeader().FrameType == FrameType.DATA) {
                                                        scheduler.addSchedulerUnit(f, stream.getStreamId());
                                                    }
                                                }
                                            }

                                            // 4️⃣ 启动 Scheduler（只启动一次）
                                            if (!schedulerStarted) {
                                                schedulerStarted = true;
                                                scheduler.schedule(ctx);
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