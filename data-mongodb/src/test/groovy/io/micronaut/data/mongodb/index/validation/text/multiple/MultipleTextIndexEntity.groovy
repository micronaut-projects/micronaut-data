package io.micronaut.data.mongodb.index.validation.multipletext

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoTextIndexed

@MongoCompoundIndex(
        name = 'compound_text_idx',
        fields = [
                @MongoCompoundIndexField(value = 'tenantId'),
                @MongoCompoundIndexField(value = 'description', text = true)
        ]
)
@MappedEntity('multiple_text_index_entities')
class MultipleTextIndexEntity {
    @Id
    @GeneratedValue
    String id

    String tenantId

    @MongoTextIndexed(name = 'field_text_idx')
    String title

    String description
}
