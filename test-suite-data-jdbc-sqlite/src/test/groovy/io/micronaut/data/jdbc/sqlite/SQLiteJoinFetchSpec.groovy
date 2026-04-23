package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractJoinFetchSpec

class SQLiteJoinFetchSpec extends AbstractJoinFetchSpec implements SQLiteTestPropertyProvider {

    boolean outerJoinSupported = false

    boolean outerFetchJoinSupported = false

    @Override
    BookRepository getBookRepository() {
        return context.getBean(SQLiteBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(SQLiteAuthorRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository getAuthorJoinLeftFetchRepository() {
        return context.getBean(SQLiteAuthorJoinLeftFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftRepository getAuthorJoinLeftRepository() {
        return context.getBean(SQLiteAuthorJoinLeftRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository getAuthorJoinRightFetchRepository() {
        return context.getBean(SQLiteAuthorJoinRightFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightRepository getAuthorJoinRightRepository() {
        return context.getBean(SQLiteAuthorJoinRightRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterRepository getAuthorJoinOuterRepository() {
        throw new UnsupportedOperationException("Full Outer Join is not supported by SQLite.")
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository getAuthorJoinOuterFetchRepository() {
        throw new UnsupportedOperationException("Full Outer Join is not supported by SQLite.")
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinFetchRepository getAuthorJoinFetchRepository() {
        return context.createBean(SQLiteAuthorJoinFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinInnerRepository getAuthorJoinInnerRepository() {
        return context.getBean(SQLiteAuthorJoinInnerRepository)
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinFetchRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinInnerRepository extends AuthorJoinTypeRepositories.AuthorJoinInnerRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinLeftFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinLeftRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinRightFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface SQLiteAuthorJoinRightRepository extends AuthorJoinTypeRepositories.AuthorJoinRightRepository {
}
