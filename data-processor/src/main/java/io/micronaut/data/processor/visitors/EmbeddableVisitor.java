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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A visitor that handles types annotated with {@link Embeddable}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class EmbeddableVisitor implements TypeElementVisitor<Embeddable, Object> {

    private final MappedEntityVisitor mappedEntityVisitor = new MappedEntityVisitor(false);

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        mappedEntityVisitor
                .visitClass(element, context);
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
