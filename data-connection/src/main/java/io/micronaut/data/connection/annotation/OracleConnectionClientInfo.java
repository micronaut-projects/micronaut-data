/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.connection.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation used to set client info for the connection. Assumed it is applied only for Oracle
 * database connections.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Connectable
public @interface OracleConnectionClientInfo {

    /**
     * If this flag is not disabled then when connection is established {@link java.sql.Connection#setClientInfo(String, String)} will be called
     * if it is connected to the Oracle database. It will issue calls to set MODULE, ACTION and CLIENT_IDENTIFIER.
     *
     * @return whether connection should trace/set client info
     */
    boolean disableClientInfoTracing() default false;

    /**
     * The module name for tracing if {@link #disableClientInfoTracing()} ()} is not set to true.
     * If not provided, then it will fall back to the name of the class currently being intercepted in {@link io.micronaut.data.connection.interceptor.ConnectableInterceptor}.
     * Currently supported only for Oracle database connections.
     *
     * @return the custom module name for tracing
     */
    String tracingModule() default "";

    /**
     * The action name for tracing if {@link #disableClientInfoTracing()} is not set to true.
     * If not provided, then it will fall back to the name of the method currently being intercepted in {@link io.micronaut.data.connection.interceptor.ConnectableInterceptor}.
     * Currently supported only for Oracle database connections.
     *
     * @return the custom module name for tracing
     */
    String tracingAction() default "";
}
