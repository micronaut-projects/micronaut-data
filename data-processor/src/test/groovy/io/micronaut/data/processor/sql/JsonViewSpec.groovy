package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.ContactView

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JsonViewSpec extends AbstractDataSpec {

    void "test JsonView repository"() {
        given:
        def repository = buildRepository('test.ContactViewRepository', """
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.ContactView;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.ORACLE)
@QueryResult(type = QueryResult.Type.JSON)
interface ContactViewRepository extends CrudRepository<ContactView, Long> {

    Long updateContactView(@Id Long id, String name);
}
""")

        def findByIdQuery = getQuery(repository.getRequiredMethod("findById", Long))
        def saveQuery = getQuery(repository.getRequiredMethod("save", ContactView))
        def updateQuery = getQuery(repository.getRequiredMethod("update", ContactView))
        def updateContactViewQuery = getQuery(repository.getRequiredMethod("updateContactView", Long, String))
        def deleteByIdQuery = getQuery(repository.getRequiredMethod("deleteById", Long))
        def deleteQuery = getQuery(repository.getRequiredMethod("delete", ContactView))
        def deleteAllQuery = getQuery(repository.getRequiredMethod("deleteAll"))
        def deleteAllIterableQuery = getQuery(repository.getRequiredMethod("deleteAll", Iterable<ContactView>))

        expect:
        findByIdQuery == 'SELECT cv.* FROM CONTACT_VIEW cv WHERE (cv.DATA.id = ?)'
        saveQuery == 'INSERT INTO CONTACT_VIEW VALUES (?)'
        updateQuery == 'UPDATE CONTACT_VIEW cv SET cv.DATA=? WHERE (cv.DATA.id = ?)'
        updateContactViewQuery == 'UPDATE CONTACT_VIEW cv SET cv.name=? WHERE (cv.DATA.id = ?)'
        deleteByIdQuery == 'DELETE  FROM CONTACT_VIEW  cv WHERE (cv.DATA.id = ?)'
        deleteQuery == 'DELETE  FROM CONTACT_VIEW  cv WHERE (cv.DATA.id = ?)'
        deleteAllQuery == 'DELETE  FROM CONTACT_VIEW  cv'
        deleteAllIterableQuery == 'DELETE  FROM CONTACT_VIEW  cv WHERE (cv.DATA.id IN (?))'
    }

    void "test JsonView repository not supported property projection"() {
        when:
        buildRepository('test.ContactViewRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.ContactView;
@JdbcRepository(dialect = Dialect.ORACLE)
interface ContactViewRepository extends GenericRepository<ContactView, Long> {

    String findNameById(Long id);
}
""")

        then:
        def thrown = thrown(RuntimeException)
        thrown.message.contains("Property name projection in entity ContactView not supported for JsonView and dialect ORACLE")
    }

    void "test JsonView repository not supported max function projection"() {
        when:
        buildRepository('test.ContactViewRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.ContactView;
@JdbcRepository(dialect = Dialect.ORACLE)
interface ContactViewRepository extends GenericRepository<ContactView, Long> {

    int findMaxAgeByName(String name);
}
""")

        then:
        def thrown = thrown(RuntimeException)
        thrown.message.contains("Function MAX projection for property age in entity ContactView not supported for JsonView and dialect ORACLE")
    }
}
