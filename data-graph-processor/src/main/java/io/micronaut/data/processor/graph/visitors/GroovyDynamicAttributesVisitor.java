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
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.DynamicAttributeHolder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Set;

/**
 * Adds {@link io.micronaut.data.graph.model.GroovyDynamicAttributeHolder} to Groovy node classes that implement
 * {@link DynamicAttributeHolder}, so that Groovy code reads and writes schemaless properties with property and subscript
 * syntax. Classes compiled by other languages are left unchanged.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GroovyDynamicAttributesVisitor implements TypeElementVisitor<Object, Object> {

    private static final String GROOVY_ELEMENT_PACKAGE = "io.micronaut.ast.groovy.";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(Node.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.isAssignable(DynamicAttributeHolder.class)) {
            return;
        }
        // Only touch Groovy types when compiling Groovy, so other compilers never load them
        if (element.getClass().getName().startsWith(GROOVY_ELEMENT_PACKAGE)) {
            GroovyHolderSupport.addGroovyHolder(element);
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
