package io.micronaut.data.document.mongodb.upsert.repo;

import io.micronaut.data.document.mongodb.upsert.model.SongEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.repository.CrudRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@MongoRepository
public abstract class SongRepository implements CrudRepository<SongEntity, String> {
    @Override
    @MongoUpdateOptions(upsert = true)
    public abstract <S extends SongEntity> S update(@Valid @NotNull S entity);

    @MongoUpdateOptions(upsert = true)
    public abstract <S extends SongEntity> S save(@Valid @NotNull S entity);
}
