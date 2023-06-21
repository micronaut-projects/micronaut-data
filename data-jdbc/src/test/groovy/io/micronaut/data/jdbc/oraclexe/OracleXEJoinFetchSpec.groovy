package io.micronaut.data.jdbc.oraclexe

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractJoinFetchSpec

class OracleXEJoinFetchSpec extends AbstractJoinFetchSpec implements OracleTestPropertyProvider {

    @Override
    BookRepository getBookRepository() {
        return context.getBean(OracleXEBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(OracleXEAuthorRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository getAuthorJoinLeftFetchRepository() {
        return context.getBean(OracleXEAuthorJoinLeftFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinLeftRepository getAuthorJoinLeftRepository() {
        return context.getBean(OracleXEAuthorJoinLeftRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository getAuthorJoinRightFetchRepository() {
        return context.getBean(OracleXEAuthorJoinRightFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinRightRepository getAuthorJoinRightRepository() {
        return context.getBean(OracleXEAuthorJoinRightRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterRepository getAuthorJoinOuterRepository() {
        return context.getBean(OracleXEAuthorJoinOuterRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository getAuthorJoinOuterFetchRepository() {
        return context.getBean(OracleXEAuthorJoinOuterFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinFetchRepository getAuthorJoinFetchRepository() {
        return context.getBean(OracleXEAuthorJoinFetchRepository)
    }

    @Override
    AuthorJoinTypeRepositories.AuthorJoinInnerRepository getAuthorJoinInnerRepository() {
        return context.getBean(OracleXEAuthorJoinInnerRepository)
    }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinFetchRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinInnerRepository extends AuthorJoinTypeRepositories.AuthorJoinInnerRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinLeftFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinLeftRepository extends AuthorJoinTypeRepositories.AuthorJoinLeftRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinOuterFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinOuterRepository extends AuthorJoinTypeRepositories.AuthorJoinOuterRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinRightFetchRepository extends AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository {
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEAuthorJoinRightRepository extends AuthorJoinTypeRepositories.AuthorJoinRightRepository {
}
