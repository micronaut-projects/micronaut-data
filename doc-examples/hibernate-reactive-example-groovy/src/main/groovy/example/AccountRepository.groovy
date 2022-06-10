package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.reactive.ReactorCrudRepository

@Repository
interface AccountRepository extends ReactorCrudRepository<Account, Long> {
}
