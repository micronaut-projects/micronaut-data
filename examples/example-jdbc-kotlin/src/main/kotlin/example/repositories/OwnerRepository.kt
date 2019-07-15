package example.repositories

import java.util.Optional

import example.domain.Owner
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface OwnerRepository : CrudRepository<Owner, Long> {

    override fun findAll(): List<Owner>

    fun findByName(name: String): Optional<Owner>
}