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
package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.JsonEntity;
import io.micronaut.data.tck.entities.SampleData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

public interface JsonEntityRepository extends CrudRepository<JsonEntity, Long> {

    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.DEFAULT, column = "json_default")
    Optional<SampleData> findJsonDefaultById(Long id);

    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.BLOB, column = "json_blob")
    Optional<SampleData> findJsonBlobById(Long id);

    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.STRING, column = "json_string")
    Optional<SampleData> findJsonStringById(Long id);

    void updateJsonStringById(@Id Long id, @Parameter SampleData jsonString);

    @Query("UPDATE json_entity SET json_blob = :jsonBlob WHERE id = :id")
    void updateJsonBlobById(Long id, SampleData jsonBlob);

    JsonEntity insert(Long id, Iterable<String> values);

    void update(@Id Long id, Iterable<String> values);

    @NonNull
    @Override
    JsonEntity save(@Valid @NotNull @NonNull JsonEntity entity);
}
