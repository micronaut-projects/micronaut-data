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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The operation context.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@SuppressWarnings("VisibilityModifier")
@Internal
public class OperationContext {
    public final AnnotationMetadata annotationMetadata;
    public final Class<?> repositoryType;
    public final List<Association> associations = Collections.emptyList();
    public final Set<Object> persisted = new HashSet<>(5);

    public OperationContext(AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
        this.annotationMetadata = annotationMetadata;
        this.repositoryType = repositoryType;
    }
}
