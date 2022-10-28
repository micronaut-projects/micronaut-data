package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import java.util.Optional

@Repository("custom")
abstract class ParentRepositoryForCustomDb : KotlinCrudRepository<Parent, Int> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: Int): Parent?

}
