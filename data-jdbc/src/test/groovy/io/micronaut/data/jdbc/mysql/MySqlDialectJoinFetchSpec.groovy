package io.micronaut.data.jdbc.mysql

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractJoinFetchSpec

/**
 * Abstract Spec class used for DBs using mysql dialect (MySql, MariaDB)
 */
abstract class MySqlDialectJoinFetchSpec extends AbstractJoinFetchSpec {

    boolean outerJoinSupported = false

    boolean outerFetchJoinSupported = false

    @Override
    BookRepository getBookRepository() {
        return context.getBean(MySqlBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MySqlAuthorRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository getAuthorJoinLeftFetchRepository() {
        return context.getBean(MySqlAuthorJoinLeftFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftRepository getAuthorJoinLeftRepository() {
        return context.getBean(MySqlAuthorJoinLeftRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository getAuthorJoinRightFetchRepository() {
        return context.getBean(MySqlAuthorJoinRightFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightRepository getAuthorJoinRightRepository() {
        return context.getBean(MySqlAuthorJoinRightRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterRepository getAuthorJoinOuterRepository() {
        throw new UnsupportedOperationException("Full Outer Join is not supported by MySql.")
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository getAuthorJoinOuterFetchRepository() {
        throw new UnsupportedOperationException("Full Outer Join is not supported by MySql.")
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinFetchRepository getAuthorJoinFetchRepository() {
        return context.getBean(MySqlAuthorJoinFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinInnerRepository getAuthorJoinInnerRepository() {
        return context.getBean(MySqlAuthorJoinInnerRepository)
    }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlAuthorJoinFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinFetchRepository {
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlAuthorJoinInnerRepository extends AuthorJoinTypeRepositories.AuthorJoinInnerRepository {
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlAuthorJoinLeftFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository {
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlAuthorJoinLeftRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftRepository {
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlAuthorJoinRightFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository {
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlAuthorJoinRightRepository extends AuthorJoinTypeRepositories.AuthorJoinRightRepository {
}
