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
package io.micronaut.data.processor.graph.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Makes every node serializable and deserializable with Micronaut Serialization. The annotations are declared on the
 * class itself as {@link Serdeable}, so that serde generates the source
 * of its serializer and deserializer.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class NodeSerdeMapper implements TypedAnnotationMapper<Node> {

    @Override
    public Class<Node> annotationType() {
        return Node.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Node> annotation, VisitorContext visitorContext) {
        // Serdeable itself rather than Serdeable.Serializable and Serdeable.Deserializable: members inherit the
        // annotations of their class, and serde reads those two on a member as a property-level override, which turns
        // source generation off
        return List.of(AnnotationValue.builder(Serdeable.class).build());
    }
}
