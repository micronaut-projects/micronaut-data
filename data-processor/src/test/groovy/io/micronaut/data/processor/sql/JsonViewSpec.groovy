package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.ContactView

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JsonViewSpec extends AbstractDataSpec {

    void "test JsonView repository"() {
        given:
        def repository = buildRepository('test.ContactViewRepository', """
import io.micronaut.data.annotation.Id;
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

    void updateByAddressStreet(String street, String name);

    void updateAgeAndName(@Id Long id, int age, String name);

    Iterable<ContactView> findAllOrderByAddressZipCodeDesc();

    String findAddressStreetById(Long id);

    Optional<ContactView> findByAddressStreet(String street);

    Optional<LocalDateTime> findStartDateTimeById(Long id);

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
        def updateAgeAndNameQuery = getQuery(repository.getRequiredMethod("updateAgeAndName", Long, int, String))
        def updateByAddressStreetQuery = getQuery(repository.getRequiredMethod("updateByAddressStreet", String, String))
        def deleteByIdQuery = getQuery(repository.getRequiredMethod("deleteById", Long))
        def deleteQuery = getQuery(repository.getRequiredMethod("delete", ContactView))
        def deleteAllQuery = getQuery(repository.getRequiredMethod("deleteAll"))
        def deleteAllIterableQuery = getQuery(repository.getRequiredMethod("deleteAll", Iterable<ContactView>))
        def findNameByIdQuery = getQuery(repository.getRequiredMethod("findNameById", Long))
        def findMaxAgeQuery = getQuery(repository.getRequiredMethod("findMaxAge"))
        def findActiveByNameQuery = getQuery(repository.getRequiredMethod("findActiveByName", String))
        def findAllOrderByStartDateTimeQuery = getQuery(repository.getRequiredMethod("findAllOrderByStartDateTime"))
        def findByAddressStreetQuery = getQuery(repository.getRequiredMethod("findByAddressStreet", String))
        def findAddressStreetByIdQuery = getQuery(repository.getRequiredMethod("findAddressStreetById", Long))
        def findAllOrderByAddressZipCodeDescQuery = getQuery(repository.getRequiredMethod("findAllOrderByAddressZipCodeDesc"))

        expect:
        findStartDateTimeByIdQuery == 'SELECT cv.DATA.startDateTime.timestamp() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        findByIdQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        saveQuery == 'INSERT INTO "CONTACT_VIEW" VALUES (?)'
        updateQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA=? WHERE (cv.DATA.id = ?)'
        updateAgeAndNameQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA= json_transform(DATA, SET \'$.age\' = ?, SET \'$.name\' = ?) WHERE (cv.DATA.id = ?)'
        updateByAddressStreetQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA= json_transform(DATA, SET \'$.name\' = ?) WHERE (cv.DATA.address.street = ?)'
        deleteByIdQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA.id = ?)'
        deleteQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA.id = ?)'
        deleteAllQuery == 'DELETE  FROM "CONTACT_VIEW"  cv'
        deleteAllIterableQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA.id IN (?))'
        findNameByIdQuery == 'SELECT cv.DATA.name.string() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        findMaxAgeQuery == 'SELECT MAX(cv.DATA.age.number()) FROM "CONTACT_VIEW" cv'
        findActiveByNameQuery == 'SELECT cv.DATA.active.number() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.name = ?)'
        findAllOrderByStartDateTimeQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv ORDER BY cv.DATA.startDateTime ASC'
        findByAddressStreetQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv WHERE (cv.DATA.address.street = ?)'
        findAddressStreetByIdQuery == 'SELECT cv.DATA.address.street.string() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.id = ?)'
        findAllOrderByAddressZipCodeDescQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv ORDER BY cv.DATA.address.zipCode DESC'
    }

}
