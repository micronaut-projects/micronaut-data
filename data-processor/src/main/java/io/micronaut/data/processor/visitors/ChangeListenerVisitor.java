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
package io.micronaut.data.processor.visitors;

import io.micronaut.data.intercept.annotation.ChangeListenerQuery;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.function.Function;
import java.util.Objects;

/**
 * Generates the ROWID reload query metadata for Oracle change listeners.
 */
public final class ChangeListenerVisitor implements TypeElementVisitor<Object, Object> {
    private static final String CHANGE_LISTENER = "io.micronaut.data.jdbc.annotation.ChangeListener";

    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return new SourcePersistentEntity(classElement, this);
        }
    };

    @Override
    public int getOrder() {
        return MappedEntityVisitor.POSITION + 1;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasAnnotation(CHANGE_LISTENER)) {
            return;
        }
        ParameterElement[] parameters = element.getParameters();
        if (parameters.length != 1) {
            return;
        }
        SourcePersistentEntityCriteriaQuery<Object> query = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver).createQuery();
        query.select(query.from(parameters[0].getType()));
        QueryResult queryResult = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE)));
        element.annotate(ChangeListenerQuery.class, builder -> builder
            .value(queryResult.getQuery() + " WHERE ROWID = ?")
            .member("entity", new AnnotationClassValue<>(parameters[0].getType().getName())));
    }
}
