package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.ContactView

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JsonViewSpec extends AbstractDataSpec {

    void "test JsonView repository"() {
        given:
        def repository = buildRepository('test.ContactViewRepository', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.ContactView;
import java.time.LocalDateTime;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.ORACLE)
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
        findStartDateTimeByIdQuery == 'SELECT cv.DATA.startDateTime.timestamp() FROM "CONTACT_VIEW" cv WHERE (cv.DATA."_id" = ?)'
        findByIdQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv WHERE (cv.DATA."_id" = ?)'
        saveQuery == 'BEGIN INSERT INTO "CONTACT_VIEW" VALUES (?) RETURNING JSON_VALUE(DATA,\'$._id\') INTO ?; END;'
        updateQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA=? WHERE (cv.DATA."_id" = ?)'
        updateAgeAndNameQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA= json_transform(DATA, SET \'$.age\' = ?, SET \'$.name\' = ?) WHERE (cv.DATA."_id" = ?)'
        updateByAddressStreetQuery == 'UPDATE "CONTACT_VIEW" cv SET cv.DATA= json_transform(DATA, SET \'$.name\' = ?) WHERE (cv.DATA.address.street = ?)'
        deleteByIdQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA."_id" = ?)'
        deleteQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA."_id" = ?)'
        deleteAllQuery == 'DELETE  FROM "CONTACT_VIEW"  cv'
        deleteAllIterableQuery == 'DELETE  FROM "CONTACT_VIEW"  cv WHERE (cv.DATA."_id" IN (?))'
        findNameByIdQuery == 'SELECT cv.DATA.name.string() FROM "CONTACT_VIEW" cv WHERE (cv.DATA."_id" = ?)'
        findMaxAgeQuery == 'SELECT MAX(cv.DATA.age.number()) FROM "CONTACT_VIEW" cv'
        findActiveByNameQuery == 'SELECT cv.DATA.active.number() FROM "CONTACT_VIEW" cv WHERE (cv.DATA.name = ?)'
        findAllOrderByStartDateTimeQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv ORDER BY cv.DATA.startDateTime ASC'
        findByAddressStreetQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv WHERE (cv.DATA.address.street = ?)'
        findAddressStreetByIdQuery == 'SELECT cv.DATA.address.street.string() FROM "CONTACT_VIEW" cv WHERE (cv.DATA."_id" = ?)'
        findAllOrderByAddressZipCodeDescQuery == 'SELECT cv.* FROM "CONTACT_VIEW" cv ORDER BY cv.DATA.address.zipCode DESC'
    }

    void "test JsonView repository with unsupported dialect"() {
        when:
        buildRepository('test.MySqlContactViewRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.ContactView;
@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlContactViewRepository extends CrudRepository<ContactView, Long> {
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('not supported by the dialect MYSQL')
    }

    void "test JsonView entity with invalid MappedProperty for id field"() {
        when:
        buildEntity('test.Person', '''
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedProperty;

@JsonView
record Person(@Id @GeneratedValue @MappedProperty("id") Long id, String name, int age) {}
''')
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("@JsonView identity @MappedProperty value cannot be set to value different than '_id'")
    }
}
