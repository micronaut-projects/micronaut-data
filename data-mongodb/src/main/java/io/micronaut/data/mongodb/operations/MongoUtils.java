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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonDateTime;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Mongo internal utils.
 */
@Internal
public final class MongoUtils {

    public static final String ID = "_id";

    private MongoUtils() {
    }

    public static BsonValue entityIdValue(ConversionService conversionService,
                                          RuntimePersistentEntity<Object> persistentEntity,
                                          Object entity,
                                          CodecRegistry codecRegistry) {
        if (persistentEntity.hasIdentity()) {
            RuntimePersistentProperty<Object> identity = persistentEntity.getIdentity();
            BeanProperty<Object, Object> property = identity.getProperty();
            Object value = property.get(entity);
            if (value != null) {
                return idValue(conversionService, persistentEntity, value, codecRegistry);
            }
        }
        throw new IllegalStateException("Cannot determine id!");
    }

    public static <T> BsonValue idValue(ConversionService conversionService,
                                        RuntimePersistentEntity<T> persistentEntity,
                                        Object idValue,
                                        CodecRegistry codecRegistry) {
        if (persistentEntity.hasIdentity()) {
            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
            if (identity instanceof Association) {
                return toBsonValue(idValue, codecRegistry);
            }
            AnnotationValue<BsonRepresentation> bsonRepresentation = identity.getAnnotationMetadata().getAnnotation(BsonRepresentation.class);
            if (bsonRepresentation != null) {
                BsonType bsonType = bsonRepresentation.getRequiredValue(BsonType.class);
                return toBsonValue(conversionService, bsonType, idValue);
            } else {
                BeanProperty<T, Object> property = identity.getProperty();
                Class<?> type = property.getType();
                if (type == String.class && identity.isGenerated() && idValue != null) {
                    return generatedStringIdValue(idValue.toString());
                }
                return toBsonValue(idValue, codecRegistry);
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

    @Nullable
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
            case DOCUMENT:
                BsonDocument bsonDocument = bsonValue.asDocument();
                Set<String> keys = bsonDocument.keySet();
                Map<String, Object> result = CollectionUtils.newHashMap(keys.size());
                for (String key : keys) {
                    result.put(key, toValue(bsonDocument.get(key, BsonNull.VALUE)));
                }
                return result;
            case ARRAY:
                return bsonValue.asArray().stream().map(MongoUtils::toValue).toList();
            default:
                throw new IllegalStateException("Not implemented for: " + bsonValue.getBsonType());
        }
    }

    public static BsonValue toBsonValue(@Nullable Object value, CodecRegistry codecRegistry) {
        if (value == null) {
            return BsonNull.VALUE;
        }
        if (value instanceof String strValue) {
            return new BsonString(strValue);
        }
        if (value instanceof Integer intValue) {
            return new BsonInt32(intValue);
        }
        if (value instanceof Long longValue) {
            return new BsonInt64(longValue);
        }
        if (value instanceof ObjectId objectId) {
            return new BsonObjectId(objectId);
        }
        // Temporal types
        if (value instanceof Instant instant) {
            return new BsonDateTime(instant.toEpochMilli());
        }
        if (value instanceof Date date) {
            return new BsonDateTime(date.getTime());
        }
        // Fallback: use the codec to encode the value into a named field within a document,
        // then extract that field to obtain the proper BsonValue. This avoids writing a scalar
        // at the root of a BsonDocumentWriter (which causes NPE for dates).
        Codec codec = codecRegistry.get(value.getClass());
        BsonDocument holder = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(holder);
        writer.writeStartDocument();
        writer.writeName("value");
        // Use default encoder context
        codec.encode(writer, value, EncoderContext.builder().build());
        writer.writeEndDocument();
        return holder.get("value", BsonNull.VALUE);
    }

    static BsonValue generatedStringIdValue(String idValue) {
        if (ObjectId.isValid(idValue)) {
            return new BsonObjectId(new ObjectId(idValue));
        }
        return new BsonString(idValue);
    }

    static BsonValue toBsonValue(ConversionService conversionService, BsonType bsonType, Object value) {
        switch (bsonType) {
            case STRING:
                return new BsonString(value.toString());
            case OBJECT_ID:
                if (value instanceof String strValue) {
                    return new BsonObjectId(new ObjectId(strValue));
                }
                if (value instanceof byte[] bytesValue) {
                    return new BsonObjectId(new ObjectId(bytesValue));
                }
                if (value instanceof Date dateValue) {
                    return new BsonObjectId(new ObjectId(dateValue));
                }
                return new BsonObjectId(conversionService.convertRequired(value, ObjectId.class));
            default:
                throw new IllegalStateException("Bson conversion to: " + bsonType + " is missing!");
        }
    }

}
