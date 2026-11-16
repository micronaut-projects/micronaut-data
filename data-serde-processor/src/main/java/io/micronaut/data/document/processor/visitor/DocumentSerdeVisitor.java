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
package io.micronaut.data.document.processor.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.document.serde.IdDeserializer;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Set;

/**
 * Keeps the runtime serdes for the document types that a generated serde would not read or write the same way.
 *
 * <p>With field access, the introspection and the query builder name a field such as {@code isOdd} after the field,
 * while a generated serde names it after its {@code isOdd()} getter, which writes {@code odd}. The runtime serdes
 * follow the introspection, so documents keep the field name.</p>
 *
 * <p>A generated id declared as a supertype of {@link String}, such as {@link java.io.Serializable}, holds the hex
 * value of an ObjectId. Only {@link IdDeserializer} reads it, and the deserializer it sets also disables the generated
 * deserializer of the type.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class DocumentSerdeVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(MappedEntity.class.getName(), Embeddable.class.getName());
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public int getOrder() {
        // Before the serde visitors, which read the annotation
        return 100;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype(MappedEntity.class) && !element.hasStereotype(Embeddable.class)) {
            return;
        }
        for (PropertyElement property : element.getBeanProperties()) {
            if (isGeneratedIdOfStringSupertype(property, context)) {
                property.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.DESERIALIZER_CLASS, IdDeserializer.class.getName()));
            }
        }
        if (usesFieldAccess(element) && element.getFields().stream().anyMatch(field -> hasBooleanGetterOfSameName(element, field))) {
            element.annotate(SerdeableGenerated.class, builder -> builder.member("skip", true));
        }
    }

    private boolean isGeneratedIdOfStringSupertype(PropertyElement property, VisitorContext context) {
        if (!property.hasStereotype(Id.class) || !property.hasStereotype(GeneratedValue.class)) {
            return false;
        }
        ClassElement type = property.getType();
        return !type.getName().equals(String.class.getName())
            && context.getClassElement(String.class).filter(string -> string.isAssignable(type)).isPresent();
    }

    private boolean usesFieldAccess(ClassElement element) {
        for (Introspected.AccessKind accessKind : element.enumValues(Introspected.class, "accessKind", Introspected.AccessKind.class)) {
            if (accessKind == Introspected.AccessKind.FIELD) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBooleanGetterOfSameName(ClassElement element, FieldElement field) {
        String name = field.getName();
        if (name.length() < 3 || !name.startsWith("is") || !Character.isUpperCase(name.charAt(2))) {
            return false;
        }
        String type = field.getType().getName();
        if (!type.equals("boolean") && !type.equals(Boolean.class.getName())) {
            return false;
        }
        return element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named(name).filter(method -> !method.hasParameters())).isPresent();
    }
}
