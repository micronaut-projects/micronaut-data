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
package io.micronaut.data.aws.dynamodb.mapper;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.util.BinaryUtils;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reader for DynamoDB results.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class DynamoDbResultReader implements ResultReader<List<Map<String, AttributeValue>>, String> {

    private int index = 0;

    @Override
    public <T> T getRequiredValue(List<Map<String, AttributeValue>> resultSet, String name, Class<T> type) throws DataAccessException {
        // Maybe should heck index out of bounds, or at least wrap in try/catch for other potential issues
        Map<String, AttributeValue> item = resultSet.get(index);
        AttributeValue attributeValue = item.get(name);
        if (attributeValue == null) {
            return null;
        }
        Object o = null;
        if (String.class.isAssignableFrom(type)) {
            o = attributeValue.getS();
        } else if (isNumber(type)) {
            o = attributeValue.getN();
        } else if (isBoolean(type)) {
            o = attributeValue.getBOOL();
        } else if (byte[].class.isAssignableFrom(type)) {
            ByteBuffer byteBuffer = attributeValue.getB();
            o = BinaryUtils.copyAllBytesFrom(byteBuffer);
        } else if (ByteBuffer.class.isAssignableFrom(type)) {
            o = attributeValue.getB();
        } else if (isSet(type)) {
            o = attributeValue.getSS();
            if (o == null) {
                o = attributeValue.getNS();
                // TODO: Iterate and convert to number if o != null ?
            }
            if (o == null) {
                // if set is of type byte[] we should convert it?
                o = attributeValue.getBS();
            }
            // if o is null then there is something wrong
        } else if (isList(type)) {
            o = attributeValue.getL();
            // convert List of AttributeValue to actual list type somehow?
        }
        // TODO: Read and convert list, set, map
        if (o == null) {
            return null;
        }
        if (type.isInstance(o)) {
            return (T) o;
        } else {
            return convertRequired(o, type);
        }
    }

    @Override
    public boolean next(List<Map<String, AttributeValue>> resultSet) {
        if (CollectionUtils.isNotEmpty(resultSet)) {
            return false;
        }
        return index++ < resultSet.size();
    }

    private <T> boolean isNumber(@NonNull  Class<T> type) {
        if (type.isPrimitive()) {
            return ClassUtils.getPrimitiveType(type.getName()).map(aClass ->
                Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
            ).orElse(false);
        } else {
            return Number.class.isAssignableFrom(type);
        }
    }

    private <T> boolean isBoolean(@NonNull  Class<T> type) {
        if (type.isPrimitive()) {
            return ClassUtils.getPrimitiveType(type.getName()).map(aClass ->
                Boolean.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
            ).orElse(false);
        } else {
            return Boolean.class.isAssignableFrom(type);
        }
    }

    private <T> boolean isSet(@NonNull Class<T> type) {
        return Set.class.isAssignableFrom(type);
    }

    private <T> boolean isList(@NonNull Class<T> type) {
        return List.class.isAssignableFrom(type);
    }
}
