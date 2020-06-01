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
package io.micronaut.data.jdbc.operations;

/**
 * The way to write the value in the query statement. E.g. Should it call {@link io.micronaut.data.runtime.mapper.QueryStatement#setString(Object, Object, String)}  or {@link io.micronaut.data.runtime.mapper.QueryStatement#setDynamic(Object, Object, io.micronaut.data.model.DataType, Object)}
 * @author Sergio del Amo
 * @since 1.1.0
 */
public enum WriteValueMode {
    DYNAMIC,
    STRING
}
