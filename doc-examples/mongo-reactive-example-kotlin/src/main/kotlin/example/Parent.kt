package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
data class Parent(val name: String,
                  @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = [Relation.Cascade.ALL])
                  val children: List<Child>,
                  @field:Id @GeneratedValue
                  val id: ObjectId? = null)
