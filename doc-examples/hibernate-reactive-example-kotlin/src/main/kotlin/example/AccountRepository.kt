package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository

@Repository
interface AccountRepository : CoroutineCrudRepository<Account, Long>
