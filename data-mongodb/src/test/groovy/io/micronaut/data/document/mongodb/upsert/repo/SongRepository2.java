package io.micronaut.data.document.mongodb.upsert.repo;

import io.micronaut.data.document.mongodb.upsert.model.SongEntity2;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.repository.CrudRepository;
import org.bson.types.ObjectId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@MongoRepository
public abstract class SongRepository2 implements CrudRepository<SongEntity2, ObjectId> {
    @Override
    @MongoUpdateOptions(upsert = true)
    public abstract <S extends SongEntity2> S update(@Valid @NotNull S entity);

    @MongoUpdateOptions(upsert = true)
    public abstract <S extends SongEntity2> S save(@Valid @NotNull S entity);
}
