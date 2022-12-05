package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@Repository
abstract class ParentRepository : KotlinCrudRepository<Parent, Int> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: Int): Parent?

}
