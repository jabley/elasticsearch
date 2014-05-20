/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.benchmark.transport.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

public class NettyEchoBenchmark {

    public static void main(String[] args) {
        final int payloadSize = 100;
        int CYCLE_SIZE = 50000;
        final long NUMBER_OF_ITERATIONS = 500000;

        ByteBuf message = Unpooled.buffer(100);
        for (int i = 0; i < message.capacity(); i++) {
            message.writeByte((byte) i);
        }

        // Configure the server.
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                        .channel(NioServerSocketChannel.class)
                        .handler(new EchoServerHandler());

        // Bind and start to accept incoming connections.
        serverBootstrap.bind(new InetSocketAddress(9000));

        final EchoClientHandler clientHandler = new EchoClientHandler();
        
        Bootstrap clientBootstrap = new Bootstrap()
                        .channel(NioSocketChannel.class)
//                        .channel(OioSocketChannel.class)
                        .handler(clientHandler);

        // Start the connection attempt.
        ChannelFuture future = clientBootstrap.connect(new InetSocketAddress("localhost", 9000));
        future.awaitUninterruptibly();
        Channel clientChannel = future.channel();

        System.out.println("Warming up...");
        for (long i = 0; i < 10000; i++) {
            clientHandler.latch = new CountDownLatch(1);
            clientChannel.writeAndFlush(message);
            try {
                clientHandler.latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Warmed up");


        long start = System.currentTimeMillis();
        long cycleStart = System.currentTimeMillis();
        for (long i = 1; i < NUMBER_OF_ITERATIONS; i++) {
            clientHandler.latch = new CountDownLatch(1);
            clientChannel.writeAndFlush(message);
            try {
                clientHandler.latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if ((i % CYCLE_SIZE) == 0) {
                long cycleEnd = System.currentTimeMillis();
                System.out.println("Ran 50000, TPS " + (CYCLE_SIZE / ((double) (cycleEnd - cycleStart) / 1000)));
                cycleStart = cycleEnd;
            }
        }
        long end = System.currentTimeMillis();
        long seconds = (end - start) / 1000;
        System.out.println("Ran [" + NUMBER_OF_ITERATIONS + "] iterations, payload [" + payloadSize + "]: took [" + seconds + "], TPS: " + ((double) NUMBER_OF_ITERATIONS) / seconds);

        clientChannel.close().awaitUninterruptibly();
        clientBootstrap.group().shutdownGracefully();
        serverBootstrap.group().shutdownGracefully();
        
        clientBootstrap.group().terminationFuture().syncUninterruptibly();
        serverBootstrap.group().terminationFuture().syncUninterruptibly();
    }

    public static class EchoClientHandler extends ChannelInboundHandlerAdapter {

        public volatile CountDownLatch latch;

        public EchoClientHandler() {
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            latch.countDown();
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.channel().close();
            super.exceptionCaught(ctx, cause);
        }

    }


    public static class EchoServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.channel().writeAndFlush(msg);
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.channel().close();
        }
    }
}