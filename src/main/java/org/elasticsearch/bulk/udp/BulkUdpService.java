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

package org.elasticsearch.bulk.udp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.ByteBufBytesReference;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.PortsRange;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.common.util.concurrent.EsExecutors.daemonThreadFactory;

/**
 */
public class BulkUdpService extends AbstractLifecycleComponent<BulkUdpService> {

    private final Client client;
    private final NetworkService networkService;

    private final boolean enabled;

    final String host;
    final String port;

    final ByteSizeValue receiveBufferSize;
    final RecvByteBufAllocator receiveBufferSizeAllocator;
    final int bulkActions;
    final ByteSizeValue bulkSize;
    final TimeValue flushInterval;
    final int concurrentRequests;

    private BulkProcessor bulkProcessor;
    private Bootstrap bootstrap;
    private ChannelFuture channel;

    @Inject
    public BulkUdpService(Settings settings, Client client, NetworkService networkService) {
        super(settings);
        this.client = client;
        this.networkService = networkService;

        this.host = componentSettings.get("host");
        this.port = componentSettings.get("port", "9700-9800");

        this.bulkActions = componentSettings.getAsInt("bulk_actions", 1000);
        this.bulkSize = componentSettings.getAsBytesSize("bulk_size", new ByteSizeValue(5, ByteSizeUnit.MB));
        this.flushInterval = componentSettings.getAsTime("flush_interval", TimeValue.timeValueSeconds(5));
        this.concurrentRequests = componentSettings.getAsInt("concurrent_requests", 4);

        this.receiveBufferSize = componentSettings.getAsBytesSize("receive_buffer_size", new ByteSizeValue(10, ByteSizeUnit.MB));
        this.receiveBufferSizeAllocator = new FixedRecvByteBufAllocator(componentSettings.getAsBytesSize("receive_predictor_size", receiveBufferSize).bytesAsInt()); 

        this.enabled = componentSettings.getAsBoolean("enabled", false);

        logger.debug("using enabled [{}], host [{}], port [{}], bulk_actions [{}], bulk_size [{}], flush_interval [{}], concurrent_requests [{}]",
                enabled, host, port, bulkActions, bulkSize, flushInterval, concurrentRequests);
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        if (!enabled) {
            return;
        }
        bulkProcessor = BulkProcessor.builder(client, new BulkListener())
                .setBulkActions(bulkActions)
                .setBulkSize(bulkSize)
                .setFlushInterval(flushInterval)
                .setConcurrentRequests(concurrentRequests)
                .build();

        EventLoopGroup workerGroup = new NioEventLoopGroup(0, daemonThreadFactory(settings, "bulk_udp_worker"));
        
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.RCVBUF_ALLOCATOR, receiveBufferSizeAllocator)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline().addLast(new Handler());
                    }
                });

        InetAddress hostAddressX;
        try {
            hostAddressX = networkService.resolveBindHostAddress(host);
        } catch (IOException e) {
            logger.warn("failed to resolve host {}", e, host);
            return;
        }
        final InetAddress hostAddress = hostAddressX;

        PortsRange portsRange = new PortsRange(port);
        final AtomicReference<Exception> lastException = new AtomicReference<>();
        boolean success = portsRange.iterate(new PortsRange.PortCallback() {
            @Override
            public boolean onPortNumber(int portNumber) {
                try {
                    channel = bootstrap.bind(new InetSocketAddress(hostAddress, portNumber)).sync();
                } catch (Exception e) {
                    lastException.set(e);
                    return false;
                }
                return true;
            }
        });
        if (!success) {
            logger.warn("failed to bind to {}/{}", lastException.get(), hostAddress, port);
            return;
        }

        logger.info("address {}", channel.channel().localAddress());
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        if (!enabled) {
            return;
        }
        if (channel != null) {
            channel.channel().close().awaitUninterruptibly();
        }
        if (bootstrap != null) {
            bootstrap.group().shutdownGracefully();
            bootstrap = null;
        }
        bulkProcessor.close();
    }

    @Override
    protected void doClose() throws ElasticsearchException {
    }

    class Handler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buffer = (ByteBuf) msg;
            logger.trace("received message size [{}]", buffer.readableBytes());
            try {
                bulkProcessor.add(new ByteBufBytesReference(buffer), false, null, null);
            } catch (Exception e1) {
                logger.warn("failed to execute bulk request", e1);
            } finally {
                buffer.release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (cause instanceof BindException) {
                // ignore, this happens when we retry binding to several ports, its fine if we fail...
                return;
            }
            logger.warn("failure caught", cause);

            super.exceptionCaught(ctx, cause);
        }
    }

    class BulkListener implements BulkProcessor.Listener {

        @Override
        public void beforeBulk(long executionId, BulkRequest request) {
            if (logger.isTraceEnabled()) {
                logger.trace("[{}] executing [{}]/[{}]", executionId, request.numberOfActions(), new ByteSizeValue(request.estimatedSizeInBytes()));
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            if (logger.isTraceEnabled()) {
                logger.trace("[{}] executed  [{}]/[{}], took [{}]", executionId, request.numberOfActions(), new ByteSizeValue(request.estimatedSizeInBytes()), response.getTook());
            }
            if (response.hasFailures()) {
                logger.warn("[{}] failed to execute bulk request: {}", executionId, response.buildFailureMessage());
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, Throwable e) {
            logger.warn("[{}] failed to execute bulk request", e, executionId);
        }
    }
}
