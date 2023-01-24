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
package io.micronaut.data.mongodb.serde;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.mongodb.operations.MongoUtils;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.bson.BsonValue;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;

/**
 * Persistent entity implementation of {@link CollectibleCodec}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.3
 */
class MappedEntityCodec<T> extends MappedCodec<T> implements CollectibleCodec<T> {

    private final boolean isGeneratedId;
    private final boolean isGeneratedObjectIdAsString;
    private final boolean isGeneratedObjectId;
    private final BeanProperty<T, Object> identityProperty;

    /**
     * Default constructor.
     *
     * @param dataSerdeRegistry The data serde registry
     * @param persistentEntity  The persistent entity
     * @param type              The type
     * @param codecRegistry     The codec registry
     */
    MappedEntityCodec(DataSerdeRegistry dataSerdeRegistry,
                      RuntimePersistentEntity<T> persistentEntity,
                      Class<T> type,
                      CodecRegistry codecRegistry) {
        super(dataSerdeRegistry, persistentEntity, type, codecRegistry);
        RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
        if (identity == null) {
            throw new IllegalStateException("Identity not found!");
        }
        identityProperty = identity.getProperty();
        isGeneratedId = identity.isAnnotationPresent(GeneratedValue.class);
        isGeneratedObjectId = isGeneratedId && identity.getType().isAssignableFrom(ObjectId.class);
        isGeneratedObjectIdAsString = !isGeneratedObjectId && identity.getType().isAssignableFrom(String.class);
    }

    @Override
    public T generateIdIfAbsentFromDocument(T document) {
        if (isGeneratedId) {
            if (isGeneratedObjectId) {
                return identityProperty.withValue(document, new ObjectId());
            } else if (isGeneratedObjectIdAsString) {
                return identityProperty.withValue(document, new ObjectId().toHexString());
            }
            throw new IllegalStateException("Cannot generate id for entity: " + persistentEntity);
        }
        return document;
    }

    @Override
    public boolean documentHasId(T document) {
        return identityProperty.get(document) != null;
    }

    @Override
    public BsonValue getDocumentId(T document) {
        return MongoUtils.idValue(null, persistentEntity, document, codecRegistry);
    }
}
