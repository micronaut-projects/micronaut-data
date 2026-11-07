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
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Fails the build when a graph repository method and the DTO it returns both declare {@link Projection}: the values
 * on the method name no property, so they go in order to the properties without their own, and one annotated property
 * moves every later value to another property.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphProjectionVisitor implements TypeElementVisitor<GraphRepository, Object> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(GraphRepository.class.getName());
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.getAnnotationValuesByType(Projection.class).isEmpty()) {
            return;
        }
        ClassElement dto = TypeUtils.getMethodProducingItemType(element);
        // Collections, pages, optionals and streams of DTOs
        while (dto != null && (dto.isAssignable(Iterable.class) || dto.isOptional() || dto.isAssignable(Stream.class))) {
            dto = dto.getFirstTypeArgument().orElse(null);
        }
        if (dto != null && dto.getBeanProperties().stream().anyMatch(property -> property.hasAnnotation(Projection.class))) {
            context.fail("Declare @Projection either on the method or on the properties of [" + dto.getName()
                + "], not both: the values on the method are matched to the properties by position", element);
        }
    }

    @Override
    public int getOrder() {
        // Before the repository visitor, which adds the projections of a JDQL query to the method
        return 100;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
