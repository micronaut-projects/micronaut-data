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
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Set;

/**
 * Fails the build when a repository of a {@link GraphRepository#nonBlocking() non-blocking} stereotype has a method
 * that does not return a reactive or asynchronous type, inherited methods included: its blocking operations throw when
 * they are called.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphNonBlockingVisitor implements TypeElementVisitor<GraphRepository, Object> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(GraphRepository.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.isTrue(GraphRepository.class, "nonBlocking")) {
            return;
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract())) {
            if (!method.isSuspend() && !TypeUtils.isReactiveOrFuture(method.getReturnType())) {
                // Reported on the repository: an inherited method may have no source to point at
                context.fail("This repository runs on non-blocking operations, so the method [" + method.getName()
                    + "] must return a reactive or asynchronous type such as Mono, Flux, Publisher or CompletableFuture, not "
                    + method.getReturnType().getName(), element);
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
