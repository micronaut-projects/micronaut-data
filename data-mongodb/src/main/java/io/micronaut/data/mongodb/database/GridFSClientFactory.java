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
package io.micronaut.data.mongodb.database;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.data.mongodb.conf.GridFSConfiguration;
import io.micronaut.data.mongodb.conf.NamedBucketConfiguration;
import io.micronaut.data.mongodb.client.GridFSClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.micronaut.data.mongodb.conf.GridFSConfiguration.DEFAULT_BUCKET_NAME;

/** Factory class for GridFSClient instances.  */
@Factory
public final class GridFSClientFactory {

    private final MongoClient mongoClient;
    private final GridFSConfiguration gridFSConfiguration;

    @Inject
    public GridFSClientFactory(MongoClient mongoClient, GridFSConfiguration gridFSConfiguration) {
        this.mongoClient = mongoClient;
        this.gridFSConfiguration = gridFSConfiguration;
    }

    @Primary
    @Singleton
    public GridFSClient defaultGridFSBucket() {

        String bucketName = gridFSConfiguration.getBucketName() != null ? gridFSConfiguration.getBucketName() : DEFAULT_BUCKET_NAME;
        String databaseName = gridFSConfiguration.getDatabase();
        int chunkSize = gridFSConfiguration.getChunkSize();

        ReadPreference readPreference = gridFSConfiguration.getReadPreference();
        ReadConcern readConcern = gridFSConfiguration.getReadConcern();
        WriteConcern writeConcern = gridFSConfiguration.getWriteConcern();
        return create(bucketName, databaseName, chunkSize, readPreference, readConcern, writeConcern);
    }

    @EachBean(NamedBucketConfiguration.class)
    @Singleton
    public GridFSClient namedGridFSBucket(NamedBucketConfiguration bucketConfiguration) {

        String bucketName = bucketConfiguration.getName();
        String databaseName = bucketConfiguration.getDatabase() != null ? bucketConfiguration.getDatabase() : gridFSConfiguration.getDatabase();
        int chunkSize = bucketConfiguration.getChunkSize() == 0 ? gridFSConfiguration.getChunkSize() : bucketConfiguration.getChunkSize();

        ReadPreference readPreference = bucketConfiguration.getReadPreference() != null ? bucketConfiguration.getReadPreference() : gridFSConfiguration.getReadPreference();
        ReadConcern readConcern = bucketConfiguration.getReadConcern() != null ? bucketConfiguration.getReadConcern() : gridFSConfiguration.getReadConcern();
        WriteConcern writeConcern = bucketConfiguration.getWriteConcern() != null ? bucketConfiguration.getWriteConcern() : gridFSConfiguration.getWriteConcern();

        return create(bucketName, databaseName, chunkSize, readPreference, readConcern, writeConcern);
    }

    private GridFSClient create(String bucketName, String databaseName, int chunkSize, ReadPreference readPreference, ReadConcern readConcern, WriteConcern writeConcern) {
        if (databaseName == null) {
            throw new BeanInstantiationException("Could not determine database name for GridFS bucket: " + bucketName);
        }

        MongoDatabase database = mongoClient.getDatabase(databaseName);
        GridFSBucket gridFSBucket = GridFSBuckets.create(database, bucketName).withChunkSizeBytes(chunkSize);

        if (readConcern != null) {
            gridFSBucket = gridFSBucket.withReadConcern(readConcern);
        }

        if (readPreference != null) {
            gridFSBucket = gridFSBucket.withReadPreference(readPreference);
        }

        if (writeConcern != null) {
            gridFSBucket = gridFSBucket.withWriteConcern(writeConcern);
        }

        return new GridFSClient(gridFSBucket);
    }
}
