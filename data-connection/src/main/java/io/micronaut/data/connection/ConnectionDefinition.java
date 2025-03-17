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
package io.micronaut.data.connection;


import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * The connection definition.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface ConnectionDefinition extends AnnotationMetadataProvider {

    /**
     * Use the default propagation value.
     */
    Propagation PROPAGATION_DEFAULT = Propagation.REQUIRED;

    /**
     * The default transaction definition.
     */
    ConnectionDefinition DEFAULT = new DefaultConnectionDefinition("DEFAULT");

    /**
     * A read only definition.
     */
    ConnectionDefinition READ_ONLY = new DefaultConnectionDefinition("READ_ONLY", true);

    /**
     * A requires new definition.
     */
    ConnectionDefinition REQUIRES_NEW = DEFAULT.withPropagation(Propagation.REQUIRES_NEW);

    /**
     * Possible propagation values.
     */
    enum Propagation {

        /**
         * Support a current connection; create a new one if none exists.
         */
        REQUIRED,

        /**
         * Support a current connection; throw an exception if no current connection.
         */
        MANDATORY,

        /**
         * Create a new connection.
         */
        REQUIRES_NEW
    }

    /**
     * Return the propagation behavior.
     *
     * @return The propagation behaviour
     */
    @NonNull
    Propagation getPropagationBehavior();

    /**
     * Return the connection timeout.
     *
     * @return The optional timeout
     */
    Optional<Duration> getTimeout();

    /**
     * Return whether this is a read-only connection.
     *
     * @return The optional read only
     */
    Optional<Boolean> isReadOnly();

    /**
     * Return the name of this connection.
     *
     * @return The optional name
     */
    @Nullable
    String getName();

    /**
     * Connection definition with specific propagation.
     * @param propagation The new propagation
     * @return A new connection definition with specified propagation
     */
    @NonNull
    ConnectionDefinition withPropagation(Propagation propagation);

    /**
     * Connection definition with specific name.
     * @param name The new name
     * @return A new connection definition with specified name
     */
    @NonNull
    ConnectionDefinition withName(String name);

    /**
     * Connection definition with new annotation metadata.
     *
     * @param annotationMetadata The new annotation metadata
     * @return A new connection definition with specified annotation metadata
     */
    @NonNull
    ConnectionDefinition withAnnotationMetadata(AnnotationMetadata annotationMetadata);

    /**
     * Create a new {@link ConnectionDefinition} for the given behaviour.
     *
     * @param propagationBehaviour The behaviour
     * @return The definition
     */
    static @NonNull ConnectionDefinition of(@NonNull Propagation propagationBehaviour) {
        return new DefaultConnectionDefinition(propagationBehaviour);
    }

    /**
     * Create a new {@link ConnectionDefinition} with a given name.
     *
     * @param name The name
     * @return The definition
     */
    static @NonNull ConnectionDefinition named(@NonNull String name) {
        return new DefaultConnectionDefinition(name);
    }

}
