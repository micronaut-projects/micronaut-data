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
package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.transaction.TransactionDefinition;

import java.util.Optional;

/**
 * Common super interface for all stored operations.
 *
 * @author graemerocher
 * @since 2.2.0
 * @see EntityOperation
 * @see PreparedQuery
 * @param <R> the result type
 */
public interface StoredDataOperation<R> extends AnnotationMetadataProvider {

    /**
     * @return If the operation defines a transaction this method returned it.
     * @since 2.2.0
     */
    default @NonNull Optional<TransactionDefinition> getTransactionDefinition() {
        return Optional.empty();
    }

    /**
     * @return The query result type
     */
    @NonNull
    Argument<R> getResultArgument();
}
