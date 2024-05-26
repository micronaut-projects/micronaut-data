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
import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Top level configuration for GridFS.
 */
@ConfigurationProperties(MongoDataConfiguration.PREFIX + GridFSConfiguration.GRID_FS)
public final class GridFSConfiguration {

    public static final String GRID_FS = ".gridfs";
    public static final String DEFAULT_BUCKET_NAME = "files";
    public static final int DEFAULT_CHUNK_SIZE = 1048576;

    /**
     * Chunk size for the bucket. Default is 1 MB.
     */
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    /**
     * Database for the bucket. If not specified, the value of micronaut.data.mongodb.gridfs.database is used.
     */
    private String database;

    /** Bucket Name. */
    private String bucketName;

    private ReadConcernLevel readConcern;
    private WriteConcernOptions writeConcern;
    private ReadPreferenceOptions readPreference;

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

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }
}
