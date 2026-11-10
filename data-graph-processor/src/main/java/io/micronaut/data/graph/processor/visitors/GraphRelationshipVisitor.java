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
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Rejects a single-valued relationship that shares its relationships with another property of the node. Saving a
 * single-valued relationship removes the others it can match, which would be those of the other property.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphRelationshipVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype(Node.class)) {
            return;
        }
        Map<String, SourcePersistentEntity> entities = new HashMap<>();
        Function<ClassElement, SourcePersistentEntity> resolver = new Function<>() {
            @Override
            public SourcePersistentEntity apply(ClassElement classElement) {
                return entities.computeIfAbsent(classElement.getName(), name -> new SourcePersistentEntity(classElement, this));
            }
        };
        List<Association> relationships = new ArrayList<>();
        for (SourcePersistentProperty property : resolver.apply(element).getPersistentProperties()) {
            if (property instanceof Association association && GraphEntities.isRelationship(association)
                && !isMissingTargetNode(association)) {
                relationships.add(association);
            }
        }
        for (int i = 0; i < relationships.size(); i++) {
            Association first = relationships.get(i);
            for (Association second : relationships.subList(i + 1, relationships.size())) {
                checkShared(first, second, context);
            }
        }
    }

    private static void checkShared(Association first, Association second, VisitorContext context) {
        if ((first.getKind().isSingleEnded() || second.getKind().isSingleEnded()) && overlap(first, second)) {
            Association single = first.getKind().isSingleEnded() ? first : second;
            Association other = single == first ? second : first;
            context.fail("The single-valued relationship [" + single.getName() + "] must own its relationship type: saving it would remove the ["
                + GraphEntities.relationshipType(single) + "] relationships of [" + other.getName() + "]", ((SourcePersistentProperty) single).getPropertyElement());
        }
    }

    private static boolean isMissingTargetNode(Association association) {
        PersistentEntity associated = association.getAssociatedEntity();
        return GraphEntities.isRelationshipEntity(associated) && GraphEntities.findTargetNode(associated) == null;
    }

    private static boolean overlap(Association first, Association second) {
        if (!GraphEntities.relationshipType(first).equals(GraphEntities.relationshipType(second))) {
            return false;
        }
        Direction firstDirection = GraphEntities.direction(first);
        Direction secondDirection = GraphEntities.direction(second);
        if (firstDirection != secondDirection && firstDirection != Direction.UNDIRECTED && secondDirection != Direction.UNDIRECTED) {
            return false;
        }
        List<String> firstLabels = GraphEntities.labels(GraphEntities.targetNodeEntity(first));
        List<String> secondLabels = GraphEntities.labels(GraphEntities.targetNodeEntity(second));
        return firstLabels.containsAll(secondLabels) || secondLabels.containsAll(firstLabels);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
