/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.testing.classic;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class ClassicServerAndRequesterTest {

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final Logger log = LogManager.getLogger(getClass());

    private HttpServer server;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = ServerBootstrap.bootstrap()
                    .setSocketConfig(
                            SocketConfig.custom()
                                    .setSoTimeout(TIMEOUT)
                                    .build())
                    .register("*", new EchoHandler())
                    .register("/no-keep-alive*", new EchoHandler() {

                        @Override
                        public void handle(
                                final ClassicHttpRequest request,
                                final ClassicHttpResponse response,
                                final HttpContext context) throws HttpException, IOException {
                            super.handle(request, response, context);
                            response.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                        }
                    })
                    .setExceptionListener(LoggingExceptionListener.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE_SERVER)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                try {
                    server.shutdown(ShutdownType.IMMEDIATE);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = RequesterBootstrap.bootstrap()
                    .setSocketConfig(SocketConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .setMaxTotal(2)
                    .setDefaultMaxPerRoute(2)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE_CLIENT)
                    .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test client");
            if (requester != null) {
                try {
                    requester.shutdown(ShutdownType.GRACEFUL);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    @Test
    public void testSequentialRequests() throws Exception {
        server.start();
        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest("POST", "/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response1.getEntity());
            Assert.assertThat(body1, CoreMatchers.equalTo("some stuff"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest("POST", "/other-stuff");
        request2.setEntity(new StringEntity("some other stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            Assert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body2 = EntityUtils.toString(response2.getEntity());
            Assert.assertThat(body2, CoreMatchers.equalTo("some other stuff"));
        }
        final ClassicHttpRequest request3 = new BasicClassicHttpRequest("POST", "/more-stuff");
        request3.setEntity(new StringEntity("some more stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response3 = requester.execute(target, request3, TIMEOUT, context)) {
            Assert.assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body3 = EntityUtils.toString(response3.getEntity());
            Assert.assertThat(body3, CoreMatchers.equalTo("some more stuff"));
        }
    }

    @Test
    public void testSequentialRequestsNonPersistentConnection() throws Exception {
        server.start();
        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest("POST", "/no-keep-alive/stuff");
        request1.setEntity(new StringEntity("some stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response1.getEntity());
            Assert.assertThat(body1, CoreMatchers.equalTo("some stuff"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest("POST", "/no-keep-alive/other-stuff");
        request2.setEntity(new StringEntity("some other stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            Assert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body2 = EntityUtils.toString(response2.getEntity());
            Assert.assertThat(body2, CoreMatchers.equalTo("some other stuff"));
        }
        final ClassicHttpRequest request3 = new BasicClassicHttpRequest("POST", "/no-keep-alive/more-stuff");
        request3.setEntity(new StringEntity("some more stuff", ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response3 = requester.execute(target, request3, TIMEOUT, context)) {
            Assert.assertThat(response3.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body3 = EntityUtils.toString(response3.getEntity());
            Assert.assertThat(body3, CoreMatchers.equalTo("some more stuff"));
        }
    }

}