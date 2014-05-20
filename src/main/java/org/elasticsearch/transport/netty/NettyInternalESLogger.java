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

import io.netty.util.internal.logging.AbstractInternalLogger;
import org.elasticsearch.common.logging.ESLogger;

/**
 *
 */
public class NettyInternalESLogger extends AbstractInternalLogger {

    /**
     *
     */
    private static final long serialVersionUID = -3069987899438401878L;
    
    private final ESLogger logger;

    public NettyInternalESLogger(ESLogger logger) {
        super("elasticsearch");
        this.logger = logger;
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(String msg, Throwable cause) {
        logger.debug(msg, cause);
    }

    @Override
    public void debug(String msg, Object param) {
        logger.debug(msg, param);
    }

    @Override
    public void debug(String msg, Object... params) {
        logger.debug(msg, params);
    }

    @Override
    public void debug(String msg, Object arg1, Object arg2) {
        logger.debug(msg, arg1, arg2);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void info(String msg, Object param) {
        logger.info(msg, param);
    }

    @Override
    public void info(String msg, Object... params) {
        logger.info(msg, params);
    }

    @Override
    public void info(String msg, Object arg1, Object arg2) {
        logger.info(msg, arg1, arg2);
    }

    @Override
    public void info(String msg, Throwable cause) {
        logger.info(msg, cause);
    }

    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    @Override
    public void warn(String msg, Object param) {
        logger.warn(msg, param);
    }

    @Override
    public void warn(String msg, Object... params) {
        logger.warn(msg, params);
    }

    @Override
    public void warn(String msg, Object arg1, Object arg2) {
        logger.warn(msg, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable cause) {
        logger.warn(msg, cause);
    }

    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    @Override
    public void error(String msg, Throwable cause) {
        logger.error(msg, cause);
    }

    @Override
    public void error(String msg, Object param) {
        logger.error(msg, param);
    }

    @Override
    public void error(String msg, Object... params) {
        logger.error(msg, params);
    }

    @Override
    public void error(String msg, Object arg1, Object arg2) {
        logger.error(msg, arg1, arg2);
    }

    @Override
    public void trace(String msg) {
        logger.trace(msg);
    }

    @Override
    public void trace(String msg, Object param) {
        logger.trace(msg, param);
    }

    @Override
    public void trace(String msg, Object... params) {
        logger.trace(msg, params);
    }

    @Override
    public void trace(String msg, Throwable cause) {
        logger.trace(msg, cause);
    }

    @Override
    public void trace(String msg, Object arg1, Object arg2) {
        logger.trace(msg, arg1, arg2);
    }
}
