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
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.ContactView;
import java.time.LocalDateTime;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.ORACLE)
@QueryResult(type = QueryResult.Type.JSON)
interface ContactViewRepository extends CrudRepository<ContactView, Long> {

    Optional<LocalDateTime> findStartDateTimeById(Long id);

    Long updateContactView(@Id Long id, String name);

    String findNameById(Long id);

    int findMaxAge();

    boolean findActiveByName(String name);

    List<ContactView> findAllOrderByStartDateTime();
}
""")

        def findStartDateTimeByIdQuery = getQuery(repository.getRequiredMethod("findStartDateTimeById", Long))
        def findByIdQuery = getQuery(repository.getRequiredMethod("findById", Long))
        def saveQuery = getQuery(repository.getRequiredMethod("save", ContactView))
        def updateQuery = getQuery(repository.getRequiredMethod("update", ContactView))
        def updateContactViewQuery = getQuery(repository.getRequiredMethod("updateContactView", Long, String))
        def deleteByIdQuery = getQuery(repository.getRequiredMethod("deleteById", Long))
        def deleteQuery = getQuery(repository.getRequiredMethod("delete", ContactView))
        def deleteAllQuery = getQuery(repository.getRequiredMethod("deleteAll"))
        def deleteAllIterableQuery = getQuery(repository.getRequiredMethod("deleteAll", Iterable<ContactView>))
        def findNameByIdQuery = getQuery(repository.getRequiredMethod("findNameById", Long))
        def findMaxAgeQuery = getQuery(repository.getRequiredMethod("findMaxAge"))
        def findActiveByNameQuery = getQuery(repository.getRequiredMethod("findActiveByName", String))
        def findAllOrderByStartDateTimeQuery = getQuery(repository.getRequiredMethod("findAllOrderByStartDateTime"))

        expect:
        findStartDateTimeByIdQuery == 'SELECT cv.DATA.startDateTime.timestamp() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        findByIdQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        saveQuery == 'INSERT INTO "CONTACT_VIEW" VALUES (?)'
        updateQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA=? WHERE (cv.DATA.id = ?)'
        updateContactViewQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.name=? WHERE (cv.DATA.id = ?)'
        deleteByIdQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA.id = ?)'
        deleteQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA.id = ?)'
        deleteAllQuery == 'DELETE  FROM "CONTACT_VIEW"  cv'
        deleteAllIterableQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA.id IN (?))'
        findNameByIdQuery == 'SELECT cv.DATA.name.string() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        findMaxAgeQuery == 'SELECT MAX(cv.DATA.age.number()) FROM "CONTACT_VIEW" cv'
        findActiveByNameQuery == 'SELECT cv.DATA.active.number() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.name = ?)'
        findAllOrderByStartDateTimeQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv ORDER BY cv.DATA.startDateTime ASC'
    }

}
