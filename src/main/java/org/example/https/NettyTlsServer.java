package org.example.https;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.example.Router;

public class NettyTlsServer {

    private final int port;
    private final SslContext sslContext;
    private final Router router;

    public NettyTlsServer(int port, SslContext sslContext, Router router) {
        this.port = port;
        this.sslContext = sslContext;
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
                            // 添加 TLS 支持
                            p.addLast("ssl", sslContext.newHandler(ch.alloc()));
                            p.addLast("https", new HttpsHandler(router));
                            // 添加自己的业务处理
                            p.addLast("handler", new SimpleChannelInboundHandler<byte[]>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                    System.out.println("Received: " + new String(msg));
                                    ctx.writeAndFlush("Hello TLS!".getBytes());
                                }

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof io.netty.buffer.ByteBuf) {
                                        io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf) msg;
                                        byte[] data = new byte[buf.readableBytes()];
                                        buf.readBytes(data);
                                        channelRead0(ctx, data);
                                    }
                                }
                            });
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Netty TLS server started on port " + port);
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

