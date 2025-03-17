package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor

@Repository
//@Join(value = "genres", type = Join.Type.LEFT_FETCH)
interface AuthorRepository : CrudRepository<Author, Long>, JpaSpecificationExecutor<Author>
