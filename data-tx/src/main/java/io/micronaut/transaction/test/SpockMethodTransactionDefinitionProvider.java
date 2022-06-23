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
package io.micronaut.transaction.test;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import org.spockframework.runtime.model.FeatureMetadata;

import java.lang.reflect.AnnotatedElement;

/**
 * Helper to retrieve Spock framework method name.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Requires(classes = FeatureMetadata.class)
@Prototype
@Internal
final class SpockMethodTransactionDefinitionProvider {

    public TransactionDefinition provide(AnnotatedElement annotatedElement) {
        FeatureMetadata featureMetadata = annotatedElement.getAnnotation(FeatureMetadata.class);
        return featureMetadata == null ? TransactionDefinition.DEFAULT : TransactionDefinition.named(featureMetadata.name());
    }

}
