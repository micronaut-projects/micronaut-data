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
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.OraclePropertyGraphMapping;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Fails the compilation of an Oracle graph repository whose entity reaches a mapping the property graph cannot store,
 * such as two relationships sharing an edge table or both sides of one relationship declaring a relationship entity.
 * The rules are those of {@link OraclePropertyGraphMapping}, which schema generation also applies at startup.
 *
 * <p>Only the nodes reachable from the entity of the repository, through its relationships and relationship entities,
 * are checked. Conflicts between nodes that only meet in the graph built from several repositories are reported when
 * the schema is generated.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class OraclePropertyGraphVisitor implements TypeElementVisitor<Repository, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!isOracleGraphRepository(element)) {
            return;
        }
        ClassElement entityType = element.getTypeArguments(GenericRepository.class).get("E");
        if (entityType == null || !entityType.hasStereotype(Node.class)) {
            return;
        }
        Map<String, SourcePersistentEntity> entities = new HashMap<>();
        Function<ClassElement, SourcePersistentEntity> resolver = new Function<>() {
            @Override
            public SourcePersistentEntity apply(ClassElement classElement) {
                return entities.computeIfAbsent(classElement.getName(), name -> new SourcePersistentEntity(classElement, this));
            }
        };
        try {
            OraclePropertyGraphMapping.checkReachable(resolver.apply(entityType));
        } catch (IllegalStateException e) {
            context.fail(String.valueOf(e.getMessage()), element);
        }
    }

    private static boolean isOracleGraphRepository(ClassElement element) {
        return (element.isInterface() || element.isAbstract())
            && element.stringValue(RepositoryConfiguration.class, "queryBuilder")
                .filter(OracleGraphQueryBuilder.class.getName()::equals)
                .isPresent();
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
