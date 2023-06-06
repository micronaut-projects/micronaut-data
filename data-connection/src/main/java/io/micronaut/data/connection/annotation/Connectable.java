/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.connection.ConnectionDefinition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation is similar to "jakarta.transaction.Transactional", allowing to create a new data source connection.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding(kind = InterceptorKind.AROUND)
public @interface Connectable {

    /**
     * Alias for {@link #connectionManager()}.
     *
     * @return The connection manager
     * @see #connectionManager()
     */
    @AliasFor(member = "connectionManager")
    String value() default "";

    /**
     * A <em>qualifier</em> name value for the connection manager.
     *
     * @return The connection manager name
     * @see #value
     */
    @AliasFor(member = "value")
    String connectionManager() default "";

    /**
     * The connection propagation type.
     * <p>Defaults to {@link ConnectionDefinition.Propagation#REQUIRED}.
     *
     * @return The propagation
     */
    ConnectionDefinition.Propagation propagation() default ConnectionDefinition.Propagation.REQUIRED;

    /**
     * The timeout for this connection (in seconds).
     * <p>Defaults to the default timeout of the underlying connection system.
     *
     * @return The timeout
     */
    int timeout() default -1;

    /**
     * {@code true} if the connection is read-only.
     * <p>Defaults to {@code false}.
     * <p>This just serves as a hint for the actual connection subsystem;
     * it will <i>not necessarily</i> cause failure of write access attempts.
     * A connection manager which cannot interpret the read-only hint will
     * <i>not</i> throw an exception when asked for a read-only connection
     * but rather silently ignore the hint.
     *
     * @return Whether is read-only connection
     */
    boolean readOnly() default false;

}
