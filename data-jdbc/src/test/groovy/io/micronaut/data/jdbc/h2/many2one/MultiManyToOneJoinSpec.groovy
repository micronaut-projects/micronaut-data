package io.micronaut.data.jdbc.h2.many2one


import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import org.jspecify.annotations.Nullable
import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.sql.JoinColumn
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification


import jakarta.persistence.ManyToOne

import static io.micronaut.data.model.query.builder.sql.Dialect.H2

@H2DBProperties
class MultiManyToOneJoinSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    RefARepository refARepository = applicationContext.getBean(RefARepository)

    @Shared
    CustomBookRepository customBookRepository = applicationContext.getBean(CustomBookRepository)

    @Shared
    UserGroupMembershipRepository userGroupMembershipRepository = applicationContext.getBean(UserGroupMembershipRepository)

    @Shared
    MyEntityRepository myEntityRepository = applicationContext.getBean(MyEntityRepository)

    @Shared
    MyOtherRepository myOtherRepository = applicationContext.getBean(MyOtherRepository)

    @Shared
    CarRepository carRepository = applicationContext.getBean(CarRepository)

    @Shared
    CarManufacturerRepository carManufacturerRepository = applicationContext.getBean(CarManufacturerRepository)

    @Shared
    CarTagRepository carTagRepository = applicationContext.getBean(CarTagRepository)

    @Shared
    FleetRepository fleetRepository = applicationContext.getBean(FleetRepository)

    @Shared
    FleetManufacturerRepository fleetManufacturerRepository = applicationContext.getBean(FleetManufacturerRepository)

    void 'test many-to-one hierarchy'() {
        given:
            RefA refA = new RefA(refB: new RefB(refC: new RefC(name: "TestXyz")))
        when:
            refARepository.save(refA)
            refA = refARepository.findById(refA.id).get()
        then:
            refA.id
            refA.refB.refC.name == "TestXyz"
        when:
            def list = refARepository.queryAll(Pageable.from(0, 10))
        then:
            list.size() == 1
            list[0].refB.refC.name == "TestXyz"
        when:
            def page = refARepository.findAll(Pageable.from(0, 10))
        then:
            page.content.size() == 1
            page.content[0].refB.refC.name == "TestXyz"
        when:
            refARepository.update(refA)
            refA = refARepository.findById(refA.id).get()
        then:
            refA.id
            refA.refB.refC.name == "TestXyz"
    }

    void "test join via non identity join column"() {
        given:
        def bookType = new BookType(id: 1, name: "Fantasy")
        customBookRepository.insertBookType(bookType.id, bookType.name)
        def customAuthor = new CustomAuthor()
        customAuthor.name = "author1"
        customAuthor.id2 = 20
        def customBook = new CustomBook()
        customBook.title = "book1"
        customBook.pages = 100
        customBook.author = customAuthor
        customBook.type = bookType
        customBookRepository.save(customBook)
        when:
        def books = customBookRepository.findAll()
        then:
        books.size() == 1
        books[0].author.id2 == 20
        when:"Read and update book title"
        customBook = customBookRepository.findById(customBook.id).orElse(null)
        customBook.type
        customBook.type.id == 1
        // Since there is no join, only id is populated
        !customBook.type.name
        customBook.title = "book1-updated"
        customBookRepository.update(customBook)
        customBook = customBookRepository.findById(customBook.id).orElse(null)
        then:"Should update without errors"
        noExceptionThrown()
        customBook.title == "book1-updated"
        cleanup:
        customBookRepository.deleteAll()
        customBookRepository.deleteBookType(bookType.id)
    }

    void "test many to one with two properties starting with same prefix"() {
        given:
        def user = new User(login: "login1")
        def area = new Area(name: "area51")
        def userGroup = new UserGroup(area: area)
        def userGroupMembership = new UserGroupMembership(user: user, userGroup: userGroup)
        userGroup.getUserAuthorizations().add(userGroupMembership)
        when:
        userGroupMembershipRepository.save(userGroupMembership)
        def listByUserLogin = userGroupMembershipRepository.findAllByUserLogin(user.login)
        def listByUserLoginAndAreaId = userGroupMembershipRepository.findAllByUserLoginAndUserGroup_AreaId(user.login, area.id)
        then:
        listByUserLogin
        listByUserLoginAndAreaId
        listByUserLogin == listByUserLoginAndAreaId
        listByUserLogin[0].userGroup.id == userGroup.id
        listByUserLogin[0].user.id == user.id
        listByUserLoginAndAreaId[0].userGroup.id == userGroup.id
        listByUserLoginAndAreaId[0].user.id == user.id
    }

    void "test many to one with entity having only id field"() {
        when:"Many to one entity is null"
        def ent = myEntityRepository.insert(new MyEntity(-1, null))
        then:"Entity id is generated"
        ent.lid != -1
        when:"Find entity by id"
        def optFound = myEntityRepository.findById(ent.getLid())
        then:"Entity is loaded and many to one entity is null"
        optFound.present
        def myEntity = optFound.get()
        !myEntity.other
        when:
        myEntityRepository.update(myEntity)
        then:
        noExceptionThrown()
        when:"Many to one entity is not null"
        def myOther = new MyOther("foo")
        myOtherRepository.insert(myOther)
        def newMyEntity = new MyEntity(-1, myOther)
        myEntityRepository.insert(newMyEntity)
        optFound = myEntityRepository.findById(newMyEntity.lid)
        then:"Many to one entity is loaded and not null"
        optFound.present
        optFound.get().other
        optFound.get().other.lid == myOther.lid
    }

    void "test issue 3851 many-to-one join with pageable sorting and pagination"() {
        given:
        carRepository.deleteAll()
        carManufacturerRepository.deleteAll()

        def alpha = carManufacturerRepository.save(new CarManufacturer(name: "Alpha"))
        def beta = carManufacturerRepository.save(new CarManufacturer(name: "Beta"))
        def delta = carManufacturerRepository.save(new CarManufacturer(name: "Delta"))
        def gamma = carManufacturerRepository.save(new CarManufacturer(name: "Gamma"))
        carRepository.save(new Car(licensePlate: "CCC", manufacturer: alpha))
        carRepository.save(new Car(licensePlate: "BBB", manufacturer: beta))
        carRepository.save(new Car(licensePlate: "AAA", manufacturer: delta))
        carRepository.save(new Car(licensePlate: "DDD", manufacturer: gamma))

        when:
        def pageable = Pageable.from(1, 2, Sort.of(Sort.Order.asc("manufacturer.name")))
        Page<Car> page = carRepository.findAll(pageable)

        then:
        page.content*.licensePlate == ["AAA", "DDD"]
        page.content*.manufacturer*.name == ["Delta", "Gamma"]
        page.totalSize == 4

        when:
        Slice<Car> slice = carRepository.getAll(pageable)

        then:
        slice.content*.licensePlate == ["AAA", "DDD"]
        slice.content*.manufacturer*.name == ["Delta", "Gamma"]

        cleanup:
        carRepository.deleteAll()
        carManufacturerRepository.deleteAll()
    }

    void "test pageable to-one fetch join with to-many predicate paginates root rows"() {
        given:
        carTagRepository.deleteAll()
        carRepository.deleteAll()
        carManufacturerRepository.deleteAll()

        def manufacturer = carManufacturerRepository.save(new CarManufacturer(name: "Acme"))
        carRepository.save(new Car(licensePlate: "AAA", manufacturer: manufacturer, tags: [
            new CarTag(name: "electric"),
            new CarTag(name: "electric")
        ]))
        carRepository.save(new Car(licensePlate: "BBB", manufacturer: manufacturer, tags: [
            new CarTag(name: "electric")
        ]))
        carRepository.save(new Car(licensePlate: "CCC", manufacturer: manufacturer, tags: [
            new CarTag(name: "electric")
        ]))

        when:
        def pageable = Pageable.from(0, 2, Sort.of(Sort.Order.asc("licensePlate")))
        Page<Car> page = carRepository.findByTagsName("electric", pageable)

        then:
        page.content*.licensePlate == ["AAA", "BBB"]
        page.totalSize == 3

        cleanup:
        carTagRepository.deleteAll()
        carRepository.deleteAll()
        carManufacturerRepository.deleteAll()
    }

    void "test issue 3851 nested to-one join through one-to-many with pageable sorting and pagination"() {
        given:
        fleetRepository.deleteAll()
        fleetManufacturerRepository.deleteAll()

        def alpha = fleetManufacturerRepository.save(new FleetManufacturer(name: "Alpha"))
        def beta = fleetManufacturerRepository.save(new FleetManufacturer(name: "Beta"))
        def gamma = fleetManufacturerRepository.save(new FleetManufacturer(name: "Gamma"))
        def delta = fleetManufacturerRepository.save(new FleetManufacturer(name: "Delta"))
        def epsilon = fleetManufacturerRepository.save(new FleetManufacturer(name: "Epsilon"))
        fleetRepository.save(new Fleet(name: "Fleet-A", vehicles: [
            new Vehicle(registrationCode: "A-1", manufacturer: alpha),
            new Vehicle(registrationCode: "A-2", manufacturer: beta)
        ]))
        fleetRepository.save(new Fleet(name: "Fleet-B", vehicles: [
            new Vehicle(registrationCode: "B-1", manufacturer: gamma)
        ]))
        fleetRepository.save(new Fleet(name: "Fleet-C", vehicles: [
            new Vehicle(registrationCode: "C-1", manufacturer: delta)
        ]))
        fleetRepository.save(new Fleet(name: "Fleet-D", vehicles: [
            new Vehicle(registrationCode: "D-1", manufacturer: epsilon)
        ]))

        when:
        def pageable = Pageable.from(1, 2, Sort.of(Sort.Order.asc("name")))
        Page<Fleet> page = fleetRepository.findAll(pageable)

        then:
        page.content*.name == ["Fleet-C", "Fleet-D"]
        page.content[0].vehicles*.manufacturer*.name.flatten() == ["Delta"]
        page.content[1].vehicles*.manufacturer*.name.flatten() == ["Epsilon"]
        page.totalSize == 4

        when:
        Slice<Fleet> slice = fleetRepository.getAll(pageable)

        then:
        slice.content*.name == ["Fleet-C", "Fleet-D"]
        slice.content[0].vehicles*.manufacturer*.name.flatten() == ["Delta"]
        slice.content[1].vehicles*.manufacturer*.name.flatten() == ["Epsilon"]

        cleanup:
        fleetRepository.deleteAll()
        fleetManufacturerRepository.deleteAll()
    }

}

@JdbcRepository(dialect = H2)
interface RefARepository extends CrudRepository<RefA, Long> {

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    Page<RefA> findAll(Pageable pageable)

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    List<RefA> queryAll(Pageable pageable)

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<RefA> findById(Long aLong)
}

@MappedEntity
class RefA {
    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    RefB refB
}

@MappedEntity
class RefB {
    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    RefC refC
}

@MappedEntity
class RefC {
    @Id
    @GeneratedValue
    Long id
    String name
}

@JdbcRepository(dialect = H2)
@Join("author")
interface CustomBookRepository extends CrudRepository<CustomBook, Long> {

    @Query("INSERT INTO custbooktype (id, name) VALUES (:id, :name)")
    void insertBookType(Long id, String name)

    @Query("DELETE FROM custbooktype WHERE id = :id")
    void deleteBookType(Long id)
}

@MappedEntity(value = "custauthor1")
class CustomAuthor {
    @GeneratedValue
    @Id
    private Long id
    private Long id2
    private String name

    Long getId() { return id }
    void setId(Long id) { this.id = id }
    Long getId2() { return id2 }
    void setId2(Long id2) { this.id2 = id2 }
    String getName() { return name }
    void setName(String name) { this.name = name }
}

@MappedEntity("custbooktype")
class BookType {
    @Id
    Long id

    String name
}

@MappedEntity(value = "custbook1")
class CustomBook {
    @GeneratedValue
    @Id
    private Long id
    private String title
    private int pages
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    @JoinColumn(name = "author_id2", referencedColumnName = "id2")
    private CustomAuthor author

    @Relation(Relation.Kind.MANY_TO_ONE)
    private BookType type

    Long getId() { return id }
    void setId(Long id) { this.id = id }
    String getTitle() { return title }
    void setTitle(String title) { this.title = title }
    int getPages() { return pages }
    void setPages(int pages) { this.pages = pages }
    CustomAuthor getAuthor() { return author }
    void setAuthor(CustomAuthor author) { this.author = author }
    BookType getType() { return type }
    void setType(BookType type) { this.type = type }
}

@MappedEntity(value = "ugm", alias = "ugm_")
class UserGroupMembership {

    @Id
    @GeneratedValue
    Long id

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    UserGroup userGroup

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    User user

    @Override
    int hashCode() {
        return Objects.hash(id)
    }

    @Override
    boolean equals(Object obj) {
        if (obj instanceof UserGroupMembership) {
            UserGroupMembership other = (UserGroupMembership) obj
            return Objects.equals(id, other.id)
        }
        return false
    }
}
@MappedEntity(value = "ug", alias = "ug_")
class UserGroup {

    @Id
    @GeneratedValue
    Long id

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "userGroup")
    Set<UserGroupMembership> userAuthorizations = new HashSet<UserGroupMembership>()

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    Area area
}
@MappedEntity(value = "a", alias = "a_")
class Area {

    @Id
    @GeneratedValue
    Long id

    String name
}
@MappedEntity(value = "u", alias = "u_")
class User {

    @Id
    @GeneratedValue
    Long id

    String login
}
@JdbcRepository(dialect = H2)
interface UserGroupMembershipRepository extends CrudRepository<UserGroupMembership, Long> {

    List<UserGroupMembership> findAllByUserLogin(String login)

    @Join(value = "userGroup.area", type = Join.Type.FETCH)
    List<UserGroupMembership> findAllByUserLoginAndUserGroup_AreaId(String login, Long uid)
}

@MappedEntity
class MyEntity {
    @Id
    @GeneratedValue
    long lid

    @ManyToOne
    final MyOther other

    MyEntity(long lid, @Nullable MyOther other) {
        this.lid = lid
        this.other = other
    }
}
@MappedEntity
class MyOther {

    @Id
    String lid

    MyOther(String lid) {
        this.lid = lid
    }

    String getLid() {
        return lid
    }
}
@JdbcRepository(dialect = H2)
interface MyEntityRepository extends CrudRepository<MyEntity, Long> {
}
@JdbcRepository(dialect = H2)
interface MyOtherRepository extends CrudRepository<MyOther, String> {
}

@JdbcRepository(dialect = H2)
interface CarRepository extends CrudRepository<Car, Long> {

    @Join(value = "manufacturer", type = Join.Type.LEFT_FETCH)
    Page<Car> findAll(Pageable pageable)

    @Join(value = "manufacturer", type = Join.Type.LEFT_FETCH)
    Slice<Car> getAll(Pageable pageable)

    @Join(value = "manufacturer", type = Join.Type.LEFT_FETCH)
    Page<Car> findByTagsName(String name, Pageable pageable)
}

@JdbcRepository(dialect = H2)
interface CarManufacturerRepository extends CrudRepository<CarManufacturer, Long> {
}

@JdbcRepository(dialect = H2)
interface CarTagRepository extends CrudRepository<CarTag, Long> {
}

@MappedEntity("the_car")
class Car {
    @Id
    @GeneratedValue
    Long id

    String licensePlate

    @Relation(Relation.Kind.MANY_TO_ONE)
    CarManufacturer manufacturer

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "car", cascade = Relation.Cascade.ALL)
    List<CarTag> tags = []
}

@MappedEntity("the_car_manufacturer")
class CarManufacturer {
    @Id
    @GeneratedValue
    Long id

    String name
}

@MappedEntity("the_car_tag")
class CarTag {
    @Id
    @GeneratedValue
    Long id

    String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    Car car
}

@JdbcRepository(dialect = H2)
interface FleetRepository extends CrudRepository<Fleet, Long> {

    @Join(value = "vehicles.manufacturer", type = Join.Type.LEFT_FETCH)
    Page<Fleet> findAll(Pageable pageable)

    @Join(value = "vehicles.manufacturer", type = Join.Type.LEFT_FETCH)
    Slice<Fleet> getAll(Pageable pageable)
}

@JdbcRepository(dialect = H2)
interface FleetManufacturerRepository extends CrudRepository<FleetManufacturer, Long> {
}

@MappedEntity("fleet_record")
class Fleet {
    @Id
    @GeneratedValue
    Long id

    String name

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "fleet", cascade = Relation.Cascade.ALL)
    List<Vehicle> vehicles = []
}

@MappedEntity("fleet_vehicle")
class Vehicle {
    @Id
    @GeneratedValue
    Long id

    String registrationCode

    @Relation(Relation.Kind.MANY_TO_ONE)
    Fleet fleet

    @Relation(Relation.Kind.MANY_TO_ONE)
    FleetManufacturer manufacturer
}

@MappedEntity("fleet_manufacturer")
class FleetManufacturer {
    @Id
    @GeneratedValue
    Long id

    String name
}
