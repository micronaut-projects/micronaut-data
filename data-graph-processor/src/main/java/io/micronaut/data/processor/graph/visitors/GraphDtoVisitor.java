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
package io.micronaut.data.processor.graph.visitors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Fails the build when a graph repository method returns a DTO that is not annotated {@link Serdeable}: graph
 * repositories read DTOs with Micronaut Serialization, and the annotation is the application's to declare.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphDtoVisitor implements TypeElementVisitor<GraphRepository, Object> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(GraphRepository.class.getName());
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.isTrue(DataMethod.class, DataMethod.META_MEMBER_DTO)) {
            return;
        }
        ClassElement dto = TypeUtils.getMethodProducingItemType(element);
        // Collections, pages, optionals and streams of DTOs
        while (dto != null && (dto.isAssignable(Iterable.class) || dto.isOptional() || dto.isAssignable(Stream.class))) {
            dto = dto.getFirstTypeArgument().orElse(null);
        }
        if (dto == null || dto.hasStereotype(Serdeable.class) || dto.hasStereotype(Serdeable.Deserializable.class)) {
            return;
        }
        context.fail("Graph repositories read DTOs with Micronaut Serialization: annotate [" + dto.getName()
            + "] with @Serdeable", element);
    }

    @Override
    public int getOrder() {
        // After the repository visitor has worked out which methods return DTOs
        return -100;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
