package io.micronaut.data.jdbc.h2.embeddedNameMapping

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import jakarta.inject.Inject
import jakarta.persistence.Embedded
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

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
            myBookRepository.insert(book)
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
            statements.join("\n") == 'CREATE TABLE "MyBook" ("id" VARCHAR(255) NOT NULL,"firstName" VARCHAR(255) NOT NULL,"lastName" VARCHAR(255) NOT NULL,"numberAge" INT NOT NULL, PRIMARY KEY("id"));'
    }

    void "test build insert"() {
        when:
            RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()
            def res = builder.createCriteriaInsert(MyBook).build(new SqlQueryBuilder())

        then:
            res.query == 'INSERT INTO "MyBook" ("firstName","lastName","numberAge","id") VALUES (?,?,?,?)'
    }

    void "test update"() {
        when:
            RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()
            def query = builder.createCriteriaUpdate(MyBook)
            query.set('id', builder.parameter(Object))
            query.set('author.firstName', builder.parameter(Object))
            query.set('author.lastName', builder.parameter(Object))
            query.set('author.detailsIncluded.numberAge', builder.parameter(Object))
            query.where(builder.equal(query.root.id(), builder.parameter(Object)))
            def res = query.build(new SqlQueryBuilder())

        then:
            res.query == 'UPDATE "MyBook" SET "id"=?,"firstName"=?,"lastName"=?,"numberAge"=? WHERE ("id" = ?)'
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
            RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()
            def query = builder.createQuery(MyBook)
            def root = query.from(MyBook)
            query.where(builder.equal(root.id(), builder.parameter(Object)))
            def q = query.build(new SqlQueryBuilder())
        then:
            q.query == 'SELECT my_book_."id",my_book_."firstName",my_book_."lastName",my_book_."numberAge" FROM "MyBook" my_book_ WHERE (my_book_."id" = ?)'
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

