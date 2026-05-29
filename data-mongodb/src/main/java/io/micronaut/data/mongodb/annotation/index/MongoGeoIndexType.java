/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.mongodb.annotation.index;

/**
 * Supported MongoDB geospatial index kinds.
 *
 * @author radovanradic
 * @since 5.1.0
 */
public enum MongoGeoIndexType {
    GEO_2D("2d"),
    GEO_2DSPHERE("2dsphere");

    private final String key;

    MongoGeoIndexType(String key) {
        this.key = key;
    }

    /**
     * @return The MongoDB key value.
     */
    public String getKey() {
        return key;
    }
}
