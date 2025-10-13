package example

import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.Sql

@MicronautTest
@Sql("classpath:embedded-relations.sql")
class EmbeddedRelationsTest(
    private val clientRepository: ClientRepository,
    private val relationshipStatusRepository: RelationshipStatusRepository,
    private val entityRepository: SampleEntityRepository
) : StringSpec({

    "client is saved with relationship status" {
        val status = relationshipStatusRepository.findById(1)

        status.shouldNotBeNull()
        status.name shouldBe "Active"

        // when
        val newClient = clientRepository.save(
            Client(
                name = "Active Client",
                relationship = Relationship(
                    status = status
                )
            )
        )

        // then
        newClient.name shouldBe "Active Client"
        newClient.relationship.status.should { savedStatus ->
            savedStatus.id shouldBe status.id
            savedStatus.name shouldBe status.name
        }
    }

    "should not update field 'example'" {
        entityRepository.save(SampleEntity(id = 1, name = "Val1", example = "Test"))
        val persistedEntity = entityRepository.getById(1)

        persistedEntity.example shouldBe null
        persistedEntity.name shouldBe "Val1"

        entityRepository.update(persistedEntity.copy(name = "Val2", example = "Changed"))
        val updatedEntity = entityRepository.getById(1)

        updatedEntity.example shouldBe null
        updatedEntity.name shouldBe "Val2"
    }

    "should not update field 'part_text'" {
        entityRepository.save(SampleEntity(id = 2, name = "NewVal", part = Part("Test")))
        val persistedEntity = entityRepository.getById(2)

        persistedEntity.part.text shouldBe null
        persistedEntity.name shouldBe "NewVal"

        entityRepository.update(persistedEntity.copy(name = "UpdatedVal", part = Part(text = "Changed")))
        val updatedEntity = entityRepository.getById(2)

        updatedEntity.part.text shouldBe null
        updatedEntity.name shouldBe "UpdatedVal"
    }
})
