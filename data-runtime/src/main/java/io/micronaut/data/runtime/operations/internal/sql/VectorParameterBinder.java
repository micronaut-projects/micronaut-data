/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.impl.VectorTextFormatter;
import io.micronaut.data.model.vector.Vector;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

@Internal
interface VectorParameterBinder {

    PreparedParameter bind(DataType dataType, @Nullable Object value);

    static @Nullable VectorParameterBinder forDialect(Dialect dialect) {
        return switch (dialect) {
            case ORACLE -> OracleVectorParameterBinder.INSTANCE;
            default -> null;
        };
    }

    record PreparedParameter(DataType dataType, @Nullable Object value) {
    }

    enum OracleVectorParameterBinder implements VectorParameterBinder {
        INSTANCE;

        @Override
        public PreparedParameter bind(DataType dataType, @Nullable Object value) {
            if (dataType != DataType.OBJECT) {
                return new PreparedParameter(dataType, value);
            }
            if (value instanceof Vector vector) {
                return new PreparedParameter(DataType.STRING, VectorTextFormatter.toText(vector));
            }
            if (value instanceof float[] floats) {
                return new PreparedParameter(DataType.STRING, Arrays.toString(floats));
            }
            if (value instanceof double[] doubles) {
                return new PreparedParameter(DataType.STRING, Arrays.toString(doubles));
            }
            if (value instanceof byte[] bytes) {
                return new PreparedParameter(DataType.STRING, Arrays.toString(bytes));
            }
            if (value != null && value.getClass().getName().equals("oracle.sql.VECTOR")) {
                return new PreparedParameter(dataType, value);
            }
            return new PreparedParameter(dataType, value);
        }
    }
}
