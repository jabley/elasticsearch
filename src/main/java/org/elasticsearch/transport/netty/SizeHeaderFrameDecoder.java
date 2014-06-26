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

package org.elasticsearch.transport.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.monitor.jvm.JvmInfo;

import java.io.StreamCorruptedException;
import java.util.List;

/**
 */
public class SizeHeaderFrameDecoder extends ByteToMessageDecoder {

    private static final long NINETY_PER_HEAP_SIZE = (long) (JvmInfo.jvmInfo().mem().heapMax().bytes() * 0.9);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        if (buffer.readableBytes() < 6) {
            return;
        }

        buffer.markReaderIndex();

        int readerIndex = buffer.readerIndex();
        if (buffer.getByte(readerIndex) != 'E' || buffer.getByte(readerIndex + 1) != 'S') {
            // we have 6 readable bytes, show 4 (should be enough)
            throw new StreamCorruptedException("invalid internal transport message format, got ("
                    + Integer.toHexString(buffer.getByte(readerIndex) & 0xFF) + ","
                    + Integer.toHexString(buffer.getByte(readerIndex + 1) & 0xFF) + ","
                    + Integer.toHexString(buffer.getByte(readerIndex + 2) & 0xFF) + ","
                    + Integer.toHexString(buffer.getByte(readerIndex + 3) & 0xFF) + ")");
        }

        int dataLen = buffer.getInt(buffer.readerIndex() + 2);
        if (dataLen <= 0) {
            throw new StreamCorruptedException("invalid data length: " + dataLen);
        }
        // safety against too large frames being sent
        if (dataLen > NINETY_PER_HEAP_SIZE) {
            throw new TooLongFrameException(
                    "transport content length received [" + new ByteSizeValue(dataLen) + "] exceeded [" + new ByteSizeValue(NINETY_PER_HEAP_SIZE) + "]");
        }

        int messageSize = dataLen + 6;

        // Make sure there's enough bytes in the buffer
        if (buffer.readableBytes() < messageSize) {
            // Reset to the marked position to read the length field again next time.
            buffer.resetReaderIndex();
            return;
        }

        ByteBuf message = buffer.readSlice(messageSize).retain();
        message.skipBytes(2); // Skip the first byte 'E' and the second byte 'S'
        out.add(message);
    }
}
