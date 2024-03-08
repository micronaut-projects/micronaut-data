package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor

@Repository
interface ChildSuspendRepository : CrudRepository<Child, Int>, CoroutineJpaSpecificationExecutor<Child>
