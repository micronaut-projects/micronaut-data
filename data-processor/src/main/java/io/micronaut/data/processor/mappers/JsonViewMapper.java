/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Adds {@link MappedEntity} and {@link io.micronaut.data.annotation.EntityRepresentation} annotations for each {@link io.micronaut.data.annotation.JsonView} annotated element.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class JsonViewMapper implements TypedAnnotationMapper<JsonView> {
    @Override
    public Class<JsonView> annotationType() {
        return JsonView.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonView> annotation, VisitorContext visitorContext) {
        AnnotationValue<MappedEntity> mappedEntityAnnotationValue = buildMappedEntityAnnotation(annotation);
        AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = buildEntityRepresentationAnnotation(annotation);
        return List.of(mappedEntityAnnotationValue, entityRepresentationAnnotationValue);
    }

    /**
     * The mapped entity for a {@link JsonView}.
     *
     * @return The mapped entity annotation
     */
    private AnnotationValue<MappedEntity> buildMappedEntityAnnotation(AnnotationValue<JsonView> jsonViewAnnotationValue) {
        final AnnotationValueBuilder<MappedEntity> builder = AnnotationValue.builder(MappedEntity.class)
                .member("value", jsonViewAnnotationValue.stringValue("value").orElse(null))
                .member("schema", jsonViewAnnotationValue.stringValue("schema").orElse(null))
                .member("alias", jsonViewAnnotationValue.stringValue("alias").orElse(null))
                .member("namingStrategy", jsonViewAnnotationValue.stringValue("namingStrategy").orElse(NamingStrategies.UnderScoreSeparatedUpperCase.class.getName())
            );
        return builder.build();
    }

    /**
     * The {@link EntityRepresentation} for a {@link JsonView}.
     *
     * @return The {@link EntityRepresentation} annotation value
     */
    private AnnotationValue<EntityRepresentation> buildEntityRepresentationAnnotation(AnnotationValue<JsonView> jsonViewAnnotationValue) {
        final AnnotationValueBuilder<EntityRepresentation> builder = AnnotationValue.builder(EntityRepresentation.class)
            .member("type", EntityRepresentation.Type.COLUMN)
            .member("columnType", EntityRepresentation.ColumnType.JSON)
            .member("jsonDataType", JsonDataType.DEFAULT)
            .member("column", jsonViewAnnotationValue.getRequiredValue("column", String.class));
        return builder.build();
    }
}
