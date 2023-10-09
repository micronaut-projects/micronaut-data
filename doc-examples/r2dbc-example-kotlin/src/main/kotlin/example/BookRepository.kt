package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.sql.Procedure
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.Flow
import reactor.core.publisher.Mono

@R2dbcRepository(dialect = Dialect.MYSQL) // <1>
interface BookRepository : CoroutineCrudRepository<Book, Long> {
    @Join("author")
    override suspend fun findById(id: Long): Book? // <2>

    @Join("author")
    override fun findAll(): Flow<Book>

    // tag::mandatory[]
    @Transactional(Transactional.TxType.MANDATORY)
    override suspend fun <S : Book> save(entity: S): S

    @Transactional(Transactional.TxType.MANDATORY)
    override fun <S : Book> saveAll(entities: Iterable<S>): Flow<S>
    // end::mandatory[]

    @Query("SELECT * FROM book WHERE title = :title")
    suspend fun customFindOne(title: String): BookDTO?

    suspend fun findOne(title: String): BookDTO?

    fun findAll(specification: CriteriaQueryBuilder<BookDTO>): Flow<BookDTO>

    // tag::procedure[]
    @Procedure
    suspend fun calculateSum(bookId: @NonNull Long): Long
    // end::procedure[]

}
