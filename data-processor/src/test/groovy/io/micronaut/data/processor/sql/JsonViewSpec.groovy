package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JsonViewSpec extends AbstractDataSpec {

    void "test JsonView repository"() {
        given:
        def repository = buildRepository('test.ContactViewRepository', """

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonViewColumn;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Address;
import io.micronaut.data.tck.entities.Metadata;
import java.time.LocalDateTime;

@JsonView(value = "CONTACT_VIEW", alias = "cv", table = "TBL_CONTACT", permissions = "INSERT UPDATE DELETE")
class ContactView {
    @Id
    private Long id;
    @JsonViewColumn(permissions = "UPDATE")
    private String name;
    private int age;
    @JsonViewColumn(permissions = "UPDATE")
    private LocalDateTime startDateTime;
    @JsonViewColumn(permissions = "UPDATE")
    private boolean active;

    @Relation(Relation.Kind.EMBEDDED)
    private Address address;
    private Metadata _metadata;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Metadata getMetadata() {
        return _metadata;
    }

    public void setMetadata(Metadata _metadata) {
        this._metadata = _metadata;
    }
}

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
        def saveQuery = getQuery(repository.findPossibleMethods("save").findFirst().get())
        def updateQuery = getQuery(repository.findPossibleMethods("update").findFirst().get())
        def updateAgeAndNameQuery = getQuery(repository.getRequiredMethod("updateAgeAndName", Long, int, String))
        def updateByAddressStreetQuery = getQuery(repository.getRequiredMethod("updateByAddressStreet", String, String))
        def deleteByIdQuery = getQuery(repository.getRequiredMethod("deleteById", Long))
        def deleteQuery = getQuery(repository.findPossibleMethods("delete").findFirst().get())
        def deleteAllQuery = getQuery(repository.getRequiredMethod("deleteAll"))
        def deleteAllIterableQuery = getQuery(repository.getRequiredMethod("deleteAll", Iterable))
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
        saveQuery == 'BEGIN INSERT INTO "CONTACT_VIEW" VALUES (?) RETURNING JSON_VALUE(DATA,\'$.id\') INTO ?; END;'
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

    void "test JsonView repository with unsupported dialect"() {
        when:
        buildRepository('test.MySqlContactViewRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JsonView(table = "TBL_USER", permissions = "INSERT UPDATE DELETE")
class UserView {
    @Id
    private Long id;
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MySqlUserViewRepository extends CrudRepository<UserView, Long> {
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('not supported by the dialect MYSQL')
    }
}
