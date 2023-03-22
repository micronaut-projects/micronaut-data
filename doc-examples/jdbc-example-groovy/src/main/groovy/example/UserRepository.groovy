
package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

import jakarta.validation.constraints.NotNull

@JdbcRepository(dialect = Dialect.H2)
interface UserRepository extends CrudRepository<User, Long> { // <1>

    @Override
    @Query("UPDATE user SET enabled = false WHERE id = :id") // <2>
    void deleteById(@NonNull @NotNull Long id)

    @Query("SELECT * FROM user WHERE enabled = false") // <3>
    List<User> findDisabled()
}
