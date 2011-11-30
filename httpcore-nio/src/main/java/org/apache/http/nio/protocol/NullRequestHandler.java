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

package org.apache.http.nio.protocol;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.protocol.HttpContext;

class NullRequestHandler implements HttpAsyncRequestHandler<Object> {

    public NullRequestHandler() {
        super();
    }

    public HttpAsyncRequestConsumer<Object> processRequest(
            final HttpRequest request, final HttpContext context) {
        return new NullRequestConsumer();
    }

    public Cancellable handle(
            final Object obj,
            final HttpAsyncServiceExchange httpexchange,
            final HttpContext context) {
        HttpResponse response = httpexchange.getResponse();
        response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        httpexchange.submitResponse(new ErrorResponseProducer(
                response, NStringEntity.create("Service not implemented"), true));
        return null;
    }

}
