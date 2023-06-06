/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.mongodb.advice;

import com.mongodb.client.ClientSession;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.mongodb.session.MongoConnectionOperations;
import io.micronaut.inject.ExecutableMethod;

import java.util.Optional;

/**
 * An interceptor that allows injecting a {@link ClientSession} that acts a proxy to lookup the connection for the current connection.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Requires(classes = ClientSession.class, beans = MongoConnectionOperations.class)
@Prototype
@Internal
@InterceptorBean(ContexualClientSessionAdvice.class)
final class ContexualClientSessionInterceptor implements MethodInterceptor<ClientSession, Object> {

    private final MongoConnectionOperations connectionOperations;

    /**
     * Default constructor.
     *
     * @param connectionOperations The connectionOperations
     */
    ContexualClientSessionInterceptor(MongoConnectionOperations connectionOperations) {
        this.connectionOperations = connectionOperations;
    }

    @Override
    public Object intercept(MethodInvocationContext<ClientSession, Object> context) {
        Optional<ConnectionStatus<ClientSession>> connectionStatus = connectionOperations.findConnectionStatus();
        if (connectionStatus.isEmpty()) {
            throw NoConnectionException.notFoundInAdvice();
        }
        final ExecutableMethod<ClientSession, Object> method = context.getExecutableMethod();

        if (method.getName().equals("close")) {
            // The session will be closed by the advice
            return null;
        }

        return method.invoke(connectionStatus.get().getConnection(), context.getParameterValues());
    }
}
