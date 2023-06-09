package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

@Repository
abstract class ParentRepository : CrudRepository<Parent, Int> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: Int): Optional<Parent>

}
