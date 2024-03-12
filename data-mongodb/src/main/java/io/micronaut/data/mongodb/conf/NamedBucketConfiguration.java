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
package io.micronaut.data.mongodb.conf;

import com.mongodb.ReadConcern;
import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import javax.validation.constraints.NotBlank;

/**
 * Configuration for class GriDFS bucket.
 */
@EachProperty(value = MongoDataConfiguration.PREFIX + GridFSConfiguration.GRID_FS + NamedBucketConfiguration.BUCKETS)
public final class NamedBucketConfiguration {

    public static final String BUCKETS = ".buckets";

    /**
     * Required. Name of the bucket
     */
    @NotBlank
    private String name;

    /**
     * Database for the bucket. If not specified, the value of micronaut.data.mongodb.gridfs.database is used.
     */
    private String database;

    /**
     * Chunk size for the bucket. Default is 1 MB
     */
    private int chunkSize;

    private ReadConcernLevel readConcern;
    private WriteConcernOptions writeConcern;
    private ReadPreferenceOptions readPreference;

    public NamedBucketConfiguration(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getDatabase() {
        return database;
    }

    public void setReadConcern(ReadConcernLevel readConcern) {
        this.readConcern = readConcern;
    }

    public void setWriteConcern(WriteConcernOptions writeConcern) {
        this.writeConcern = writeConcern;
    }

    public void setReadPreference(ReadPreferenceOptions readPreference) {
        this.readPreference = readPreference;
    }

    public ReadConcern getReadConcern() {
        return (this.readConcern != null) ? new ReadConcern(this.readConcern) : null;
    }

    public WriteConcern getWriteConcern() {
        return (this.writeConcern != null) ? this.writeConcern.getValue() : null;
    }

    public ReadPreference getReadPreference() {
        return (this.readPreference != null) ? this.readPreference.getValue() : null;
    }

}
