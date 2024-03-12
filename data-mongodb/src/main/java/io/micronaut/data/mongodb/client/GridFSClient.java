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
package io.micronaut.data.mongodb.client;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import io.micronaut.context.annotation.Bean;
import io.micronaut.data.exceptions.DataAccessException;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Client for a given (or default) GridFS bucket.
 */
@Bean
public class GridFSClient {

    /** Bucket backing the client instance. */
    private final GridFSBucket bucket;

    public GridFSClient(GridFSBucket bucket) {
        this.bucket = bucket;
    }

    /**
     * Upload a file to GridFS bucket.
     *
     * @param path     Path to the temporary file
     * @param fileName Filename for the uploaded file
     * @param metadata Metadata for the uploaded file
     * @return ObjectId for the uploaded file
     */
    public ObjectId uploadFile(Path path, String fileName, Map<String, String> metadata) {

        Document document = new Document();
        if (metadata != null) {
            document.putAll(metadata);
        }

        try (InputStream streamToUploadFrom = new FileInputStream(path.toFile())) {
            GridFSUploadOptions options = new GridFSUploadOptions()
                    .metadata(document);
            return bucket.uploadFromStream(fileName, streamToUploadFrom, options);
        } catch (IOException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

    /**
     * Download a file for given id.
     *
     * @param id ObjectId of the given file
     * @return Optional Path for the downloaded file
     */
    public Optional<Path> downloadFile(ObjectId id) {
        try {
            GridFSFile file = bucket.find(Filters.eq(id)).first();
            if (file != null) {
                Path tempFile = Files.createTempFile("gridfs-" + id + "-", ".tmp");
                FileOutputStream destination = new FileOutputStream(tempFile.toFile());
                bucket.downloadToStream(id, destination);
                destination.flush();
                return Optional.of(tempFile);
            } else {
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

}
