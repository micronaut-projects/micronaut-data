package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonRepresentation;
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

    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.NATIVE, column = "json_native")
    Optional<SampleData> findJsonNativeById(Long id);

    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.BLOB, column = "json_blob")
    Optional<SampleData> findJsonBlobById(Long id);

    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.STRING, column = "json_string")
    Optional<SampleData> findJsonStringById(Long id);

    void updateJsonStringById(@Id Long id, @Parameter SampleData jsonString);

    @Query("UPDATE json_entity SET json_blob = :jsonBlob WHERE id = :id")
    void updateJsonBlobById(Long id, SampleData jsonBlob);

    @NonNull
    @Override
    JsonEntity save(@Valid @NotNull @NonNull JsonEntity entity);
}
