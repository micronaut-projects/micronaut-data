package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractJoinFetchSpec

class SqlServerJoinFetchSpec extends AbstractJoinFetchSpec implements MSSQLTestPropertyProvider {

    @Override
    BookRepository getBookRepository() {
        return context.getBean(MSBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MSAuthorRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository getAuthorJoinLeftFetchRepository() {
        return context.getBean(MSAuthorJoinLeftFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftRepository getAuthorJoinLeftRepository() {
        return context.getBean(MSAuthorJoinLeftRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository getAuthorJoinRightFetchRepository() {
        return context.getBean(MSAuthorJoinRightFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightRepository getAuthorJoinRightRepository() {
        return context.getBean(MSAuthorJoinRightRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterRepository getAuthorJoinOuterRepository() {
        return context.getBean(MSAuthorJoinOuterRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository getAuthorJoinOuterFetchRepository() {
        return context.getBean(MSAuthorJoinOuterFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinFetchRepository getAuthorJoinFetchRepository() {
        return context.getBean(MSAuthorJoinFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinInnerRepository getAuthorJoinInnerRepository() {
        return context.getBean(MSAuthorJoinInnerRepository)
    }
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinFetchRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinInnerRepository extends AuthorJoinTypeRepositories.AuthorJoinInnerRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinLeftFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinLeftRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinOuterFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinOuterRepository extends AuthorJoinTypeRepositories.AuthorJoinOuterRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinRightFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository {
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface MSAuthorJoinRightRepository extends AuthorJoinTypeRepositories.AuthorJoinRightRepository {
}
