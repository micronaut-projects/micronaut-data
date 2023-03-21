package io.micronaut.data.jdbc.h2.embeddedNameMapping

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.persistence.Embedded

@MicronautTest
@H2DBProperties
class CustomEmbeddedNameMapping extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    MyBookRepository myBookRepository = applicationContext.getBean(MyBookRepository)

    void 'test embedded custom name strategy'() {
        when:
            MyBook book = new MyBook(
                    id: "1",
                    author: new EmbeddedAuthor(
                            firstName: "Jean-Jaques",
                            lastName: "Rousseau",
                            detailsIncluded: new EmbeddedAuthorDetails(numberAge: 33)
                    )
            )
            myBookRepository.save(book)
            book = myBookRepository.findById("1").get()

        then:
            book.author
            book.author.firstName == "Jean-Jaques"
            book.author.lastName == "Rousseau"

        when:
            book.author.lastName = "Xyz"
            myBookRepository.update(book)
            book = myBookRepository.findById("1").get()

        then:
            book.author
            book.author.firstName == "Jean-Jaques"
            book.author.lastName == "Xyz"
    }

    void "test build create"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(MyBook))

        then:
            statements.join("\n") == 'CREATE TABLE "MyBook" ("id" VARCHAR(255) NOT NULL,"authorFirstName" VARCHAR(255) NOT NULL,"authorLastName" VARCHAR(255) NOT NULL,"authorDetailsIncludedNumberAge" INT NOT NULL);'
    }

    void "test build insert"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def res = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, getRuntimePersistentEntity(MyBook))

        then:
            res.query == 'INSERT INTO "MyBook" ("authorFirstName","authorLastName","authorDetailsIncludedNumberAge","id") VALUES (?,?,?,?)'
    }

    void "test update"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def entity = getRuntimePersistentEntity(MyBook)
            def res = encoder.buildUpdate(
                    QueryModel.from(entity).idEq(new QueryParameter("id")),
                    ['id', 'author.firstName', 'author.lastName', 'author.detailsIncluded.numberAge']
            )

        then:
            res.query == 'UPDATE "MyBook" SET "id"=?,"authorFirstName"=?,"authorLastName"=?,"authorDetailsIncludedNumberAge"=? WHERE ("id" = ?)'
            res.parameters == [
                    '1':'id',
                    '2':'author.firstName',
                    '3':'author.lastName',
                    '4':'author.detailsIncluded.numberAge',
                    '5':'id'
            ]
    }

    void "test build query"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def q = encoder.buildQuery(QueryModel.from(getRuntimePersistentEntity(MyBook)).idEq(new QueryParameter("xyz")))
        then:
            q.query == 'SELECT my_book_."id",my_book_."authorFirstName",my_book_."authorLastName",my_book_."authorDetailsIncludedNumberAge" FROM "MyBook" my_book_ WHERE (my_book_."id" = ?)'
    }

    @Shared
    Map<Class, RuntimePersistentEntity> entities = [:]

    // entities have instance compare in some cases
    private RuntimePersistentEntity getRuntimePersistentEntity(Class type) {
        RuntimePersistentEntity entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }


}

@JdbcRepository(dialect = Dialect.H2)
interface MyBookRepository extends CrudRepository<MyBook, String> {
}

@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
class MyBook {
    @Id
    String id
    @Embedded
    EmbeddedAuthor author
}

@Embeddable
class EmbeddedAuthor {
    String firstName, lastName
    @Embedded
    EmbeddedAuthorDetails detailsIncluded
}

@Embeddable
class EmbeddedAuthorDetails {
    int numberAge
}


