/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.transaction.support;


import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.TransactionalAdvice;

import java.time.Duration;
import java.util.Arrays;

/**
 * Transaction utils.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class TransactionUtil {

    private static final TransactionDefinition NO_TRANSACTION = new TransactionDefinition() {
    };

    private TransactionUtil() {
    }

    /**
     * Creates a transaction definition from a given name and annotation metadata provider
     *
     * @param name                       The name
     * @param annotationMetadataProvider The annotation metadata
     * @return the transaction definition
     */
    @NonNull
    public static TransactionDefinition getTransactionDefinition(String name, AnnotationMetadataProvider annotationMetadataProvider) {
        AnnotationValue<TransactionalAdvice> annotation = annotationMetadataProvider.getAnnotation(TransactionalAdvice.class);
        if (annotation == null) {
            return TransactionDefinition.DEFAULT;
        }

        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setName(name);
        definition.setReadOnly(annotation.isTrue("readOnly"));
        annotation.intValue("timeout").ifPresent(value -> definition.setTimeout(Duration.ofSeconds(value)));
        final Class[] rollbackFor = annotation.classValues("rollbackFor");
        //noinspection unchecked
        definition.setRollbackOn(Arrays.asList(rollbackFor));
        final Class[] noRollbackFors = annotation.classValues("noRollbackFor");
        //noinspection unchecked
        definition.setDontRollbackOn(Arrays.asList(noRollbackFors));
        annotation.enumValue("propagation", TransactionDefinition.Propagation.class)
                .ifPresent(definition::setPropagationBehavior);
        annotation.enumValue("isolation", TransactionDefinition.Isolation.class)
                .ifPresent(definition::setIsolationLevel);
        return definition;
    }

}
