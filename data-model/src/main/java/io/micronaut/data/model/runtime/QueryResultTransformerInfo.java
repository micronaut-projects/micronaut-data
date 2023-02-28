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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.NonNull;

/**
 * The information about query result transformer info for the method.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class QueryResultTransformerInfo {

    private final String columnName;
    private final String mediaType;

    public QueryResultTransformerInfo(@NonNull String columnName, @NonNull String mediaType) {
        this.columnName = columnName;
        this.mediaType = mediaType;
    }

    /**
     * @return the column name from which result will be read and transformed
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * @return the media type that column result produced
     */
    public String getMediaType() {
        return mediaType;
    }
}
