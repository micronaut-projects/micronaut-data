/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.date;

/**
 * Used by Micronaut Data to assign a time stamp to entity fields labeled with {@link io.micronaut.data.annotation.DateCreated} or
 * {@link io.micronaut.data.annotation.DateUpdated}.
 *
 * @param <T> the return type of the time. When used with JPA the type that is returned should be the type that is
 *           defined in the entity. When used with JDBC, the type defined should always be {@link java.sql.Date} or {@link java.sql.Date}.
 */
public interface DateTimeProvider<T> {

    /**
     * Returns the time to be used for the timestamp.
     *
     * @return The resulting time.
     */
    T getNow();
}
