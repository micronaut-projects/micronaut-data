/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.runtime.operations;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

/**
 * The transaction of a write that begins one of its own. Its connection carries the annotation metadata of the
 * operation, as the connection of a write outside a transaction does, so that per-method connection settings apply.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphWriteTransaction extends DefaultTransactionDefinition {

    private final ConnectionDefinition connectionDefinition;

    private GraphWriteTransaction(AnnotationMetadata annotationMetadata) {
        this.connectionDefinition = ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata);
    }

    /**
     * The definition of the transaction of an operation.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @return The definition
     */
    public static TransactionDefinition of(AnnotationMetadata annotationMetadata) {
        return new GraphWriteTransaction(annotationMetadata);
    }

    @Override
    public ConnectionDefinition getConnectionDefinition() {
        return connectionDefinition;
    }
}
