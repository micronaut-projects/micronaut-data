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

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.micronaut.core.annotation.AnnotationMetadata;
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
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
    public <E> com.fasterxml.jackson.databind.node. @Nullable ObjectNode serialize(RuntimePersistentEntity<E> persistentEntity, E bean, Argument<E> type) {
        com.fasterxml.jackson.databind.node.ObjectNode result = serialize(bean, type);
        if (result == null) {
            return null;
        }
        if (persistentEntity.hasIdentity()) {
            writeIdentity(persistentEntity, bean, result);
        }
        writeVersion(persistentEntity, bean, result);
        return result;
    }

    private <E> void writeIdentity(RuntimePersistentEntity<E> persistentEntity, E bean, com.fasterxml.jackson.databind.node.ObjectNode result) {
        RuntimePersistentProperty<E> identity = persistentEntity.getIdentity();
        String runtimeName = runtimeIdentityName(persistentEntity);
        if (runtimeName != null && result.has(runtimeName)) {
            result.set(identity.getName(), result.remove(runtimeName));
        }
        if (!identity.getName().equals(Constants.INTERNAL_ID)) {
            Object value = identity.getProperty().get(bean);
            String id = value == null ? null : value.toString();
            result.set(Constants.INTERNAL_ID, id == null ? NullNode.getInstance() : new TextNode(id));
        }
    }

    private static <E> void writeVersion(RuntimePersistentEntity<E> persistentEntity, E bean, com.fasterxml.jackson.databind.node.ObjectNode result) {
        String versionField = CosmosEntity.get(persistentEntity).getVersionField();
        if (versionField == null) {
            return;
        }
        RuntimePersistentProperty<E> versionProperty = persistentEntity.getPropertyByName(versionField);
        if (versionProperty != null && !versionProperty.getName().equals(Constants.ETAG_FIELD_NAME)) {
            Object value = versionProperty.getProperty().get(bean);
            if (value != null) {
                result.set(Constants.ETAG_FIELD_NAME, new TextNode(value.toString()));
            }
        }
    }

    /**
     * Serializes given bean to the given type which will be {@link com.fasterxml.jackson.databind.node.ObjectNode} or {@link com.fasterxml.jackson.databind.JsonNode}.
     *
     * @param bean the bean being serialized to JSON
     * @param type the argument type
     * @param <O> the type to be returned
     * @return the serialized bean to JSON (JsonNode or ObjectNode)
     */
    @Nullable
    public <O extends com.fasterxml.jackson.databind.JsonNode> O serialize(Object bean, Argument<?> type) {
        try {
            Serializer.EncoderContext encoderContext = serdeRegistry.newEncoderContext(null);
            Serializer<? super Object> typeSerializer = serdeRegistry.findSerializer(type);
            Serializer<Object> serializer = typeSerializer.createSpecific(encoderContext, type);
            JsonNodeEncoder encoder = JsonNodeEncoder.create();
            serializer.serialize(encoder, encoderContext, type, bean);
            // First serialize to Micronaut Serde tree model and then convert it to Jackson's tree model
            JsonNode jsonNode = encoder.getCompletedValue();
            try (JsonParser jsonParser = JsonNodeTreeCodec.getInstance().treeAsTokens(jsonNode, ObjectReadContext.empty())) {
                return (O) JacksonNodeConverter.toJackson2JsonNode(objectMapper.readTree(jsonParser));
            }
        } catch (IOException e) {
            throw new DataAccessException("Failed to serialize: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes from {@link com.fasterxml.jackson.databind.node.ObjectNode} to the given persistent entity bean type.
     *
     * @param persistentEntity the persistent entity
     * @param objectNode the object node (JSON representation)
     * @param type the argument type
     * @param <E> the entity type
     * @param <R> the type to be returned
     * @return the deserialized object of T type
     */
    @Nullable
    public  <E, R> R deserialize(RuntimePersistentEntity<E> persistentEntity, com.fasterxml.jackson.databind.node.ObjectNode objectNode, Argument<R> type) {
        RuntimePersistentProperty<?> identity = persistentEntity.getIdentity();
        if (identity != null && !identity.getName().equals(Constants.INTERNAL_ID)) {
            // Remove the internal id field if there is no such field in the entity
            objectNode.remove(Constants.INTERNAL_ID);
        }
        withSerializedIdentity(persistentEntity, objectNode);
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
     * Moves the identity of a stored document to the field the deserializer reads it from.
     *
     * @param persistentEntity the persistent entity
     * @param objectNode the object node (JSON representation)
     * @return the given object node
     */
    com.fasterxml.jackson.databind.node.ObjectNode withSerializedIdentity(RuntimePersistentEntity<?> persistentEntity, com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
        String runtimeName = runtimeIdentityName(persistentEntity);
        if (runtimeName != null) {
            String storedName = persistentEntity.getIdentity().getName();
            if (objectNode.has(storedName)) {
                objectNode.set(runtimeName, objectNode.remove(storedName));
            }
        }
        return objectNode;
    }

    /**
     * Finds the name a naming strategy applied at runtime gives the identity. Such a strategy doesn't apply to the
     * stored document: the queries and the partition key path use the property name of the identity.
     *
     * @param persistentEntity the persistent entity
     * @return the name, or null if the serde writes the identity under the name stored in the document
     */
    @Nullable
    private String runtimeIdentityName(RuntimePersistentEntity<?> persistentEntity) {
        if (!persistentEntity.hasIdentity()) {
            return null;
        }
        RuntimePersistentProperty<?> identity = persistentEntity.getIdentity();
        AnnotationMetadata annotationMetadata = identity.getAnnotationMetadata();
        if (annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent()) {
            // An explicit name, or a naming strategy resolved during annotation processing
            return null;
        }
        try (Serializer.EncoderContext encoderContext = serdeRegistry.newEncoderContext(null)) {
            Class<? extends PropertyNamingStrategy> namingStrategyClass = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
                .or(() -> persistentEntity.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING))
                .map(type -> type.asSubclass(PropertyNamingStrategy.class))
                .orElse(null);
            PropertyNamingStrategy namingStrategy = namingStrategyClass != null
                ? encoderContext.findNamingStrategy(namingStrategyClass)
                : encoderContext.getSerdeConfiguration().map(SerdeConfiguration::getPropertyNamingStrategy).orElse(null);
            if (namingStrategy == null) {
                return null;
            }
            String name = namingStrategy.translate(identity.getProperty());
            return identity.getName().equals(name) ? null : name;
        } catch (IOException e) {
            throw new DataAccessException("Failed to resolve the identity name of " + persistentEntity.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes from {@link com.fasterxml.jackson.databind.node.ObjectNode} to the given bean type.
     *
     * @param objectNode the object node (JSON representation)
     * @param type the argument type
     * @param <T> the type to be returned
     * @return the deserialized object of T type
     */
    @Nullable
    public  <T> T deserialize(com.fasterxml.jackson.databind.node.ObjectNode objectNode, Argument<T> type) {
        try {
            Deserializer.DecoderContext decoderContext = serdeRegistry.newDecoderContext(null);
            Deserializer<? extends T> typeDeserializer = serdeRegistry.findDeserializer(type);
            Deserializer<? extends T> deserializer = typeDeserializer.createSpecific(decoderContext, type);
            ObjectNode jackson3ObjectNode = JacksonNodeConverter.toJackson3ObjectNode(objectNode);
            JsonParser parser = jackson3ObjectNode.traverse(ObjectReadContext.empty());
            if (!parser.hasCurrentToken()) {
                parser.nextToken();
            }
            final Decoder decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS);
            return deserializer.deserialize(decoder, decoderContext, type);
        } catch (IOException e) {
            throw new DataAccessException("Failed to deserialize: " + e.getMessage(), e);
        }
    }
}
