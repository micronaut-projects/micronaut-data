package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@Repository
interface AccountRepository : KotlinCrudRepository<Account, Long>
