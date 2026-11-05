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
package io.micronaut.data.graph.processor.visitors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Rejects database-assigned identifiers of a primitive type. Such a property cannot be unset, and the value it starts
 * with is an identifier the database also assigns, so a new node could not be told from a saved one.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphIdentityVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype(Node.class) && !element.hasStereotype(RelationshipProperties.class)) {
            return;
        }
        for (PropertyElement property : element.getBeanProperties()) {
            if (property.hasStereotype(Id.class)
                && property.hasStereotype(GeneratedValue.class)
                && !property.hasStereotype(AutoPopulated.class)
                && property.getType().isPrimitive()) {
                context.fail("A database-assigned identifier cannot be primitive, use " + wrapper(property.getType().getName()) + ": " + property.getName(), property);
            }
        }
    }

    private static String wrapper(String primitive) {
        return primitive.equals("int") ? "Integer" : Character.toUpperCase(primitive.charAt(0)) + primitive.substring(1);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
