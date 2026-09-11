package io.micronaut.data.mongodb.index.validation.textweight

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoTextIndexed
import io.micronaut.data.repository.CrudRepository

@MongoRepository
interface InvalidTextWeightEntityRepository extends CrudRepository<InvalidTextWeightEntity, String> {
}

@MappedEntity('invalid_text_weight_entities')
class InvalidTextWeightEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'invalid_text_weight_idx', weight = 0)
    String name
}
