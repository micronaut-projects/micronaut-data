package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.document.mongodb.entities.ComplexEntity;
import io.micronaut.data.document.mongodb.entities.ComplexValue;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@MongoRepository
public interface ComplexRepository extends CrudRepository<ComplexEntity, String> {

    Optional<ComplexValue> findComplexValueById(String id);

    Optional<String> findSimpleValueById(String id);

    List<ComplexValue> findAllComplexValue();
}
