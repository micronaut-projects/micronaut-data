package io.micronaut.data.jdbc.postgres

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect

import io.micronaut.data.tck.repositories.AuthorJoinTypeRepositories
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.tests.AbstractJoinFetchSpec

class PostgresJoinFetchSpec extends AbstractJoinFetchSpec implements PostgresTestPropertyProvider {

    @Override
    BookRepository getBookRepository() {
        return context.getBean(PostgresBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(PostgresAuthorRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository getAuthorJoinLeftFetchRepository() {
        return context.getBean(PostgresAuthorJoinLeftFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftRepository getAuthorJoinLeftRepository() {
        return context.getBean(PostgresAuthorJoinLeftRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository getAuthorJoinRightFetchRepository() {
        return context.getBean(PostgresAuthorJoinRightFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightRepository getAuthorJoinRightRepository() {
        return context.getBean(PostgresAuthorJoinRightRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterRepository getAuthorJoinOuterRepository() {
        return context.getBean(PostgresAuthorJoinOuterRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository getAuthorJoinOuterFetchRepository() {
        return context.getBean(PostgresAuthorJoinOuterFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinFetchRepository getAuthorJoinFetchRepository() {
        return context.getBean(PostgresAuthorJoinFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinInnerRepository getAuthorJoinInnerRepository() {
        return context.getBean(PostgresAuthorJoinInnerRepository)
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinFetchRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinInnerRepository extends AuthorJoinTypeRepositories.AuthorJoinInnerRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinLeftFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinLeftRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinOuterFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinOuterRepository extends AuthorJoinTypeRepositories.AuthorJoinOuterRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinRightFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository {
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresAuthorJoinRightRepository extends AuthorJoinTypeRepositories.AuthorJoinRightRepository {
}
