package io.micronaut.data.mongodb.index.validation.text.adjacency

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.repository.CrudRepository

@MongoRepository
interface InvalidCompoundTextAdjacencyEntityRepository extends CrudRepository<InvalidCompoundTextAdjacencyEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_text_adjacency_idx',
        fields = [
                @MongoCompoundIndexField(value = 'tenantId'),
                @MongoCompoundIndexField(value = 'title', text = true, weight = 2),
                @MongoCompoundIndexField(value = 'createdAt', direction = MongoIndexDirection.DESC),
                @MongoCompoundIndexField(value = 'description', text = true, weight = 3)
        ]
)
@MappedEntity('invalid_compound_text_adjacency_entities')
class InvalidCompoundTextAdjacencyEntity {
    @Id
    @GeneratedValue
    String id

    String tenantId

    String title

    Long createdAt

    String description
}
