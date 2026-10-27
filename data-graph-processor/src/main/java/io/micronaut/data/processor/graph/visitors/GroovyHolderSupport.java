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

import io.micronaut.ast.groovy.visitor.GroovyNativeElement;
import io.micronaut.data.graph.model.GroovyDynamicAttributeHolder;
import io.micronaut.inject.ast.ClassElement;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;

/**
 * Changes the Groovy AST. Loaded only while compiling Groovy.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class GroovyHolderSupport {

    private GroovyHolderSupport() {
    }

    /**
     * Adds {@link GroovyDynamicAttributeHolder} to the class unless it already implements it.
     *
     * @param element The Groovy class element
     */
    static void addGroovyHolder(ClassElement element) {
        if (element.isAssignable(GroovyDynamicAttributeHolder.class)) {
            return;
        }
        if (element.getNativeType() instanceof GroovyNativeElement nativeElement
            && nativeElement.annotatedNode() instanceof ClassNode classNode) {
            classNode.addInterface(ClassHelper.make(GroovyDynamicAttributeHolder.class));
        }
    }
}
