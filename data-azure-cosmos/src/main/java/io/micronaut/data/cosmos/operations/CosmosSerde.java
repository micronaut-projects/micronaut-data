/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.cosmos.operations;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.common.CosmosEntity;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Serialize and deserialize Cosmos documents.
 *
 * @author radovanradic
 * @since 3.9.0
 */
@Singleton
@Internal
final class CosmosSerde {

    private final SerdeRegistry serdeRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected CosmosSerde(SerdeRegistry serdeRegistry) {
        this.serdeRegistry = serdeRegistry;
    }

    /**
     * Serializes given persistent object to the {@link com.fasterxml.jackson.databind.node.ObjectNode}.
     *
     * @param persistentEntity the persistent entity
     * @param bean the bean being serialized to JSON
     * @param type the argument type
     * @param <E> the entity type
     * @return the serialized bean to JSON (JsonNode or ObjectNode)
     */
    public <E> ObjectNode serialize(RuntimePersistentEntity<E> persistentEntity, E bean, Argument<E> type) {
        ObjectNode result = serialize(bean, type);
        RuntimePersistentProperty<E> identity = persistentEntity.getIdentity();
        if (identity != null && !identity.getName().equals(Constants.INTERNAL_ID)) {
            Object value = identity.getProperty().get(bean);
            String id = value == null ? null : value.toString();
            result.set(Constants.INTERNAL_ID, new TextNode(id));
        }
        CosmosEntity cosmosEntity = CosmosEntity.get(persistentEntity);
        String versionField = cosmosEntity.getVersionField();
        if (versionField != null) {
            RuntimePersistentProperty<E> versionProperty = persistentEntity.getPropertyByName(versionField);
            if (versionProperty != null && !versionProperty.getName().equals(Constants.ETAG_FIELD_NAME)) {
                Object value = versionProperty.getProperty().get(bean);
                if (value != null) {
                    result.set(Constants.ETAG_FIELD_NAME, new TextNode(value.toString()));
                }
            }
        }
        return result;
    }

    /**
     * Serializes given bean to the given type which will be {@link com.fasterxml.jackson.databind.node.ObjectNode} or {@link com.fasterxml.jackson.databind.JsonNode}.
     *
     * @param bean the bean being serialized to JSON
     * @param type the argument type
     * @param <O> the type to be returned
     * @return the serialized bean to JSON (JsonNode or ObjectNode)
     */
    public <O extends com.fasterxml.jackson.databind.JsonNode> O serialize(Object bean, Argument<?> type) {
        try {
            Serializer.EncoderContext encoderContext = serdeRegistry.newEncoderContext(null);
            Serializer<? super Object> typeSerializer = serdeRegistry.findSerializer(type);
            Serializer<Object> serializer = typeSerializer.createSpecific(encoderContext, type);
            JsonNodeEncoder encoder = JsonNodeEncoder.create();
            serializer.serialize(encoder, encoderContext, type, bean);
            // First serialize to Micronaut Serde tree model and then convert it to Jackson's tree model
            JsonNode jsonNode = encoder.getCompletedValue();
            try (JsonParser jsonParser = JsonNodeTreeCodec.getInstance().treeAsTokens(jsonNode)) {
                return objectMapper.readTree(jsonParser);
            }
        } catch (IOException e) {
            throw new DataAccessException("Failed to serialize: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes from {@link ObjectNode} to the given persistent entity bean type.
     *
     * @param persistentEntity the persistent entity
     * @param objectNode the object node (JSON representation)
     * @param type the argument type
     * @param <E> the entity type
     * @param <R> the type to be returned
     * @return the deserialized object of T type
     */
    public  <E, R> R deserialize(RuntimePersistentEntity<E> persistentEntity, ObjectNode objectNode, Argument<R> type) {
        RuntimePersistentProperty<?> identity = persistentEntity.getIdentity();
        if (identity != null && !identity.getName().equals(Constants.INTERNAL_ID)) {
            // Remove the internal id field if there is no such field in the entity
            objectNode.remove(Constants.INTERNAL_ID);
        }
        CosmosEntity cosmosEntity = CosmosEntity.get(persistentEntity);
        String versionField = cosmosEntity.getVersionField();
        if (versionField != null) {
            RuntimePersistentProperty<E> versionProperty = persistentEntity.getPropertyByName(versionField);
            if (versionProperty != null && !versionProperty.getName().equals(Constants.ETAG_FIELD_NAME)) {
                final com.fasterxml.jackson.databind.JsonNode versionValue = objectNode.get(Constants.ETAG_FIELD_NAME);
                objectNode.remove(Constants.ETAG_FIELD_NAME);
                objectNode.set(versionProperty.getName(), versionValue);
            }
        }
        return deserialize(objectNode, type);
    }

    /**
     * Deserializes from {@link ObjectNode} to the given bean type.
     *
     * @param objectNode the object node (JSON representation)
     * @param type the argument type
     * @param <T> the type to be returned
     * @return the deserialized object of T type
     */
    public  <T> T deserialize(ObjectNode objectNode, Argument<T> type) {
        try {
            Deserializer.DecoderContext decoderContext = serdeRegistry.newDecoderContext(null);
            Deserializer<? extends T> typeDeserializer = serdeRegistry.findDeserializer(type);
            Deserializer<? extends T> deserializer = typeDeserializer.createSpecific(decoderContext, type);
            JsonParser parser = objectNode.traverse();
            if (!parser.hasCurrentToken()) {
                parser.nextToken();
            }
            final Decoder decoder = JacksonDecoder.create(parser, Object.class);
            return deserializer.deserialize(decoder, decoderContext, type);
        } catch (IOException e) {
            throw new DataAccessException("Failed to deserialize: " + e.getMessage(), e);
        }
    }
}
