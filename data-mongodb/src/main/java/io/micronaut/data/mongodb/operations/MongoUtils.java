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
package io.micronaut.data.mongodb.operations;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;

/**
 * Mongo internal utils.
 */
@Internal
public final class MongoUtils {

    public static final String ID = "_id";

    private MongoUtils() {
    }

    public static BsonValue entityIdValue(ConversionService conversionService,
                                          RuntimePersistentEntity<?> persistentEntity,
                                          Object entity,
                                          CodecRegistry codecRegistry) {
        RuntimePersistentProperty<?> identity = persistentEntity.getIdentity();
        if (identity != null) {
            BeanProperty property = identity.getProperty();
            return idValue(conversionService, persistentEntity, property.get(entity), codecRegistry);
        }
        throw new IllegalStateException("Cannot determine id!");
    }

    public static BsonValue idValue(ConversionService conversionService,
                                    RuntimePersistentEntity<?> persistentEntity,
                                    Object idValue,
                                    CodecRegistry codecRegistry) {
        RuntimePersistentProperty<?> identity = persistentEntity.getIdentity();
        if (identity != null) {
            if (identity instanceof Association) {
                return toBsonValue(conversionService, idValue, codecRegistry);
            }
            AnnotationValue<BsonRepresentation> bsonRepresentation = identity.getAnnotationMetadata().getAnnotation(BsonRepresentation.class);
            if (bsonRepresentation != null) {
                BsonType bsonType = bsonRepresentation.getRequiredValue(BsonType.class);
                return toBsonValue(conversionService, bsonType, idValue);
            } else {
                BeanProperty property = identity.getProperty();
                Class<?> type = property.getType();
                if (type == String.class && idValue != null) {
                    return new BsonObjectId(new ObjectId(idValue.toString()));
                }
                return toBsonValue(conversionService, idValue, codecRegistry);
            }
        }
        throw new IllegalStateException("Cannot determine id!");
    }

    static Bson filterById(ConversionService conversionService,
                           RuntimePersistentEntity<?> persistentEntity,
                           Object value,
                           CodecRegistry codecRegistry) {
        BsonValue id = idValue(conversionService, persistentEntity, value, codecRegistry);
        return new BsonDocument().append(ID, id);
    }

    static <T> T toValue(BsonDocument bsonDocument, Class<T> resultClass, CodecRegistry codecRegistry) {
        return codecRegistry.get(resultClass).decode(bsonDocument.asBsonReader(), DecoderContext.builder().build());
    }

    static Object toValue(BsonValue bsonValue) {
        switch (bsonValue.getBsonType()) {
            case STRING:
                return bsonValue.asString().getValue();
            case INT32:
                return bsonValue.asInt32().getValue();
            case INT64:
                return bsonValue.asInt64().getValue();
            case DOUBLE:
                return bsonValue.asDouble().getValue();
            case DECIMAL128:
                return bsonValue.asDecimal128().getValue();
            case BOOLEAN:
                return bsonValue.asBoolean().getValue();
            case DATE_TIME:
                return Instant.ofEpochMilli(bsonValue.asDateTime().getValue());
            case NULL:
                return null;
            default:
                throw new IllegalStateException("Not implemented for: " + bsonValue.getBsonType());
        }
    }

    public static BsonValue toBsonValue(ConversionService conversionService, Object value, CodecRegistry codecRegistry) {
        if (value == null) {
            return BsonNull.VALUE;
        }
        if (value instanceof String) {
            return new BsonString((String) value);
        }
        if (value instanceof Integer) {
            return new BsonInt32((Integer) value);
        }
        if (value instanceof Long) {
            return new BsonInt64((Long) value);
        }
        if (value instanceof ObjectId) {
            return new BsonObjectId((ObjectId) value);
        }
        return BsonDocumentWrapper.asBsonDocument(value, codecRegistry).toBsonDocument();
    }

    static BsonValue toBsonValue(ConversionService conversionService, BsonType bsonType, Object value) {
        switch (bsonType) {
            case STRING:
                return new BsonString(value.toString());
            case OBJECT_ID:
                if (value instanceof String) {
                    return new BsonObjectId(new ObjectId((String) value));
                }
                if (value instanceof byte[]) {
                    return new BsonObjectId(new ObjectId((byte[]) value));
                }
                if (value instanceof Date) {
                    return new BsonObjectId(new ObjectId((Date) value));
                }
                return new BsonObjectId(conversionService.convertRequired(value, ObjectId.class));
            default:
                throw new IllegalStateException("Bson conversion to: " + bsonType + " is missing!");
        }
    }

}
