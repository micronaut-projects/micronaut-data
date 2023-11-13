package example

import io.micronaut.data.annotation.Where

interface UserRepositoryWithWhere {
    // ...
    @Where("@.enabled = false")
    fun findDisabled(): List<User?>?
}
