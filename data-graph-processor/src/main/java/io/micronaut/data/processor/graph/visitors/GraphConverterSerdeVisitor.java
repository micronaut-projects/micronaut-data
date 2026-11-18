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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.document.serde.CustomConverterDeserializer;
import io.micronaut.data.document.serde.CustomConverterSerializer;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Has serde read and write a property of a graph entity that declares an attribute converter through the converter.
 * This is done per entity because an annotation mapper of {@link MappedProperty} would also apply to the entities of
 * other modules on the processor path, such as JDBC, and change their JSON.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphConverterSerdeVisitor implements TypeElementVisitor<Object, Object> {

    private static final List<String> ATTRIBUTE_CONVERTERS = List.of(
        AttributeConverter.class.getName(),
        "jakarta.persistence.AttributeConverter",
        "javax.persistence.AttributeConverter"
    );

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype(Node.class) && !element.hasStereotype(RelationshipProperties.class)) {
            return;
        }
        for (PropertyElement property : element.getBeanProperties()) {
            property.stringValue(MappedProperty.class, "converter").flatMap(context::getClassElement).ifPresent(converter -> {
                ClassElement persistedType = persistedType(converter);
                property.annotate(SerdeConfig.class, builder -> {
                    if (persistedType != null) {
                        builder.member(SerdeConfig.SERIALIZE_AS, new AnnotationClassValue<>(persistedType.getName()));
                        builder.member(SerdeConfig.DESERIALIZE_AS, new AnnotationClassValue<>(persistedType.getName()));
                    }
                    builder.member(SerdeConfig.SERIALIZER_CLASS, CustomConverterSerializer.class);
                    builder.member(SerdeConfig.DESERIALIZER_CLASS, CustomConverterDeserializer.class);
                });
            });
        }
    }

    private static @Nullable ClassElement persistedType(ClassElement converter) {
        ClassElement type = converter.getGenericType();
        for (String attributeConverter : ATTRIBUTE_CONVERTERS) {
            Map<String, ClassElement> arguments = type.getTypeArguments(attributeConverter);
            if (!arguments.isEmpty()) {
                return arguments.get("Y");
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        // Before the serde visitors, which read the annotation
        return 100;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
