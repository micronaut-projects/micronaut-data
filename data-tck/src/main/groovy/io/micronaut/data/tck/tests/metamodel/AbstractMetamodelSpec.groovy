package io.micronaut.data.tck.tests.metamodel

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Book_
import io.micronaut.data.tck.entities.Chapter
import io.micronaut.data.tck.entities.Child
import io.micronaut.data.tck.entities.Child_
import io.micronaut.data.tck.entities.Client
import io.micronaut.data.tck.entities.EmbeddableClass
import io.micronaut.data.tck.entities.EmbeddedOwner
import io.micronaut.data.tck.entities.EmployeeFieldAccess
import io.micronaut.data.tck.entities.EmployeeId
import io.micronaut.data.tck.entities.EmployeeMixedAccess
import io.micronaut.data.tck.entities.EmployeeMixedAccessEmbeddedId
import io.micronaut.data.tck.entities.EmployeePropertyAccess
import io.micronaut.data.tck.entities.Genre
import io.micronaut.data.tck.entities.OrderPk
import io.micronaut.data.tck.entities.Page
import io.micronaut.data.tck.entities.Publisher
import io.micronaut.data.tck.entities.PurchaseOrder
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.entities.Train
import io.micronaut.data.tck.entities.TrainSpecs
import io.micronaut.data.tck.entities.Train_
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BasicTypesRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.ChapterRepository
import io.micronaut.data.tck.repositories.ChildRepository
import io.micronaut.data.tck.repositories.ClientCategoryRepository
import io.micronaut.data.tck.repositories.ClientRepository
import io.micronaut.data.tck.repositories.EmbeddedOwnerRepository
import io.micronaut.data.tck.repositories.EmployeeFieldAccessRepository
import io.micronaut.data.tck.repositories.EmployeeMixedAccessEmbeddedIdRepository
import io.micronaut.data.tck.repositories.EmployeeMixedAccessRepository
import io.micronaut.data.tck.repositories.EmployeePropertyAccessRepository
import io.micronaut.data.tck.repositories.GenreRepository
import io.micronaut.data.tck.repositories.PageRepository
import io.micronaut.data.tck.repositories.PublisherRepository
import io.micronaut.data.tck.repositories.PurchaseOrderRepository
import io.micronaut.data.tck.repositories.TrainRepository
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Field
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import static io.micronaut.data.tck.repositories.ChildRepository.Specification.nameEquals
import static io.micronaut.data.tck.repositories.ChildRepository.Specification.ageGreaterThan
import static io.micronaut.data.tck.repositories.ChildRepository.Specification.idEquals

abstract class AbstractMetamodelSpec extends Specification {

    abstract GenreRepository getGenreRepository();

    abstract BookRepository getBookRepository();

    abstract PublisherRepository getPublisherRepository()

    abstract AuthorRepository getAuthorRepository();

    abstract BasicTypesRepository getBasicTypeRepository();

    abstract ChildRepository getChildRepository();

    abstract EmbeddedOwnerRepository getEmbeddedOwnerRepository()

    abstract EmployeePropertyAccessRepository getEmployeePropertyAccessRepository()

    abstract EmployeeFieldAccessRepository getEmployeeFieldAccessRepository()

    abstract EmployeeMixedAccessRepository getEmployeeMixedAccessRepository()

    abstract EmployeeMixedAccessEmbeddedIdRepository getEmployeeMixedAccessEmbeddedIdRepository()

    abstract ClientRepository getClientRepository()

    abstract PurchaseOrderRepository getPurchaseOrderRepository()

    abstract ClientCategoryRepository getClientCategoryRepository()

    abstract TrainRepository getTrainRepository()

    abstract ChapterRepository getChapterRepository()

    abstract PageRepository getPageRepository()

    abstract void populateClientAndCategories()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void setup() {
        pageRepository.deleteAll();
        chapterRepository.deleteAll()
        bookRepository.deleteAll()
        chapterRepository.deleteAll()
        genreRepository.deleteAll()
        publisherRepository.deleteAll()
        authorRepository.deleteAll()
        childRepository.deleteAll()
        embeddedOwnerRepository.deleteAll()
        employeeFieldAccessRepository.deleteAll()
        employeeMixedAccessEmbeddedIdRepository.deleteAll()
        employeeMixedAccessRepository.deleteAll()
        employeePropertyAccessRepository.deleteAll()
        clientRepository.deleteAll()
        clientCategoryRepository.deleteAll()
        purchaseOrderRepository.deleteAll()
        employeeMixedAccessEmbeddedIdRepository.deleteAll()
    }

    void "can query by inherited id using static metamodel"() {
        given:
        def c1 = new Child(1L, "Alice", 10L)
        def c2 = new Child(2L, "Bob", 20L)
        getChildRepository().saveAll([c1, c2])

        when:
        def result = getChildRepository().findAll(idEquals(2L))

        then:
        result.size() == 1
        result.first().id == 2L
        result.first().name == "Bob"
        result.first().age == 20L
    }

    void "can query by inherited name and declared age using static metamodel"() {
        given:
        def c1 = new Child(3L, "Carol", 30L)
        def c2 = new Child(4L, "Carol", 5L)
        getChildRepository().saveAll([c1, c2])

        when:
        def result = getChildRepository().findAll(
                nameEquals("Carol").and(ageGreaterThan(10L)),
                Sort.of(Sort.Order.asc(Child_.ID))
        )

        then:
        result.size() == 1
        result.first().id == 3L
        result.first().name == "Carol"
        result.first().age == 30L
    }

    void "field access entity can query using static metamodel"() {
        given:
        def e1 = new EmployeeFieldAccess(null, "Alice", 100_000d)
        def e2 = new EmployeeFieldAccess(null, "Bob", 50_000d)
        getEmployeeFieldAccessRepository().saveAll([e1, e2])

        when:
        def result = getEmployeeFieldAccessRepository().findAll(
                EmployeeFieldAccessRepository.Specification.salaryBiggerThan(80_000d)
        )

        then:
        result.size() == 1
        result.first().name == "Alice"
        result.first().id != null
    }

    void "property access entity can query using static metamodel"() {
        given:
        def e1 = new EmployeePropertyAccess(null, "Carol", 120_000d)
        def e2 = new EmployeePropertyAccess(null, "Dave", 70_000d)
        getEmployeePropertyAccessRepository().saveAll([e1, e2])

        when:
        def result = getEmployeePropertyAccessRepository().findAll(
                EmployeePropertyAccessRepository.Specification.nameEquals("Carol")
        )

        then:
        result.size() == 1
        result.first().name == "Carol"
        result.first().salary == 120_000d
        result.first().id != null
    }

    void "mixed access entity default property access fieldWithoutAccessors is not persistent"() {
        given:
        def e = new EmployeeMixedAccess(null, "Eve", 90_000d)
        getEmployeeMixedAccessRepository().save(e)

        when:
        def result = getEmployeeMixedAccessRepository().findAll(EmployeeMixedAccessRepository.Specification.nameEquals("Eve"))

        then:
        result.size() == 1
        result.first().name == "Eve"
        result.first().id != null
    }

    void "can query by embedded attribute using static metamodel"() {
        given:
        def a = new EmbeddedOwner()
        a.setOwnerName("A")
        a.setEmbedded(new EmbeddableClass("X", 10L, 1L, 1.5d))

        def b = new EmbeddedOwner()
        b.setOwnerName("B")
        b.setEmbedded(new EmbeddableClass("Y", 99L, 2L, 2.5d))

        getEmbeddedOwnerRepository().saveAll([a, b])

        when:
        def result = getEmbeddedOwnerRepository().findAll(
                EmbeddedOwnerRepository.Specification.withEmbeddedName("Y")
        )

        then:
        result.size() == 1
        result.first().ownerName == "B"
        result.first().embedded.embeddedName == "Y"
    }

    void "can query by embedded id parts using static metamodel"() {
        given:
        def p1 = new PurchaseOrder()
        p1.setId(new OrderPk("t1", 1L))
        p1.setDescription("first")
        p1.setDetails(new EmbeddableClass("X", 10L, 1L, 1.5d))

        def p2 = new PurchaseOrder()
        p2.setId(new OrderPk("t1", 2L))
        p2.setDescription("second")
        p2.setDetails(new EmbeddableClass("X", 10L, 1L, 1.5d))

        getPurchaseOrderRepository().saveAll([p1, p2])

        when:
        def result = getPurchaseOrderRepository().findAll(
                PurchaseOrderRepository.Specification.tenantIdEquals("t1")
                        .and(PurchaseOrderRepository.Specification.orderNoEquals(2L))
        )

        then:
        result.size() == 1
        result.first().description == "second"
        result.first().id.tenantId == "t1"
        result.first().id.orderNo == 2L
    }

    void "can query by second embedded alongside embedded id using static metamodel"() {
        given:
        def p = new PurchaseOrder()
        p.setId(new OrderPk("t2", 10L))
        p.setDescription("has-details")
        p.setDetails(new EmbeddableClass("DETAILS", 7L, 3L, 9.9d))

        getPurchaseOrderRepository().save(p)

        when:
        def orders = getPurchaseOrderRepository().findAll(
                PurchaseOrderRepository.Specification.withEmbeddedName("DETAILS")
        )

        then:
        orders.size() == 1
        orders.first().id.orderNo == 10L
    }

    void "can filter by Author.name and Author.nickName (nullable column) via join"() {
        given:
        def a1 = author("Frank Herbert", null)
        def a2 = author("Isaac Asimov", "asimov")
        getAuthorRepository().saveAll([a1, a2])

        bookRepository.saveAll([
                book("Dune", 412, a1),
                book("Foundation", 255, a2)
        ])

        when: "filter by author name"
        def byName = bookRepository.findAll(BookRepository.Specification.authorNameEquals("Frank Herbert"))

        then:
        byName*.title == ["Dune"]

        when: "filter by nickname"
        def byNick = bookRepository.findAll(BookRepository.Specification.withAuthorNickName("asimov"))

        then:
        byNick*.title == ["Foundation"]

        when: "filter by NULL nickname"
        def nullNick = bookRepository.findAll(BookRepository.Specification.withAuthorNickNameIsNull())

        then:
        nullNick*.title == ["Dune"]
    }

    void "can join one-to-one Genre and filter by Genre.genreName"() {
        given:
        def a = author("Author", null);
        def g1 = genre("Sci-Fi");
        def g2 = genre("Drama")

        authorRepository.save(a)
        getGenreRepository().saveAll([g1, g2])

        def b1 = book("Dune", 412, a);
        b1.genre = g1
        def b2 = book("Hamlet", 50, a);
        b2.genre = g2

        bookRepository.saveAll([b1, b2])

        when:
        def result = bookRepository.findAll(BookRepository.Specification.withGenreName("Sci-Fi"))

        then:
        result.size() == 1
        result.first().title == "Dune"
        result.first().genre.genreName == "Sci-Fi"
    }

    void "can join many-to-one Publisher and filter by Publisher.zipCode"() {
        given:
        def a = author("Author", null); authorRepository.save(a)

        def p1 = publisher("12345")
        def p2 = publisher("99999")

        publisherRepository.saveAll([p1, p2])

        def b1 = book("Book A", 100, a); b1.publisher = p1
        def b2 = book("Book B", 110, a); b2.publisher = p2
        bookRepository.saveAll([b1, b2])

        when:
        def result = bookRepository.findAll(BookRepository.Specification.withPublisherZipCode("12345"))

        then:
        result*.title == ["Book A"]
        result.first().publisher.zipCode == "12345"
    }

    void "can persist pages and chapters via cascade and query using joins"() {
        given:
        def a = author("Author", null); authorRepository.save(a)

        def b = book("Complex", 999, a)

        def p1 = new Page(num: 1); p1.book = b
        def p2 = new Page(num: 2); p2.book = b
        b.pages = [p1, p2]

        def ch1 = new Chapter(title: "Intro", pages: 10); ch1.book = b
        def ch2 = new Chapter(title: "Advanced", pages: 50); ch2.book = b
        b.chapters = [ch1, ch2]

        bookRepository.save(b)

        when: "filter by page num"
        def byPage = bookRepository.findAll(BookRepository.Specification.withPageNum(2L))

        then:
        byPage*.title == ["Complex"]

        when: "filter by chapter title"
        def byChapterTitle = bookRepository.findAll(BookRepository.Specification.withChapterTitle("Advanced"))

        then:
        byChapterTitle*.title == ["Complex"]

        when: "filter by chapter pages >= 20"
        def byChapterPages = bookRepository.findAll(BookRepository.Specification.withChapterPagesGreaterThanOrEqualTo(20))

        then:
        byChapterPages*.title == ["Complex"]
    }

    void "many-to-many students: can join by Student.name"() {
        given:
        def a = author("Author", null); authorRepository.save(a)

        def s1 = new Student("Alice")
        def s2 = new Student("Bob")

        def b1 = book("Book A", 100, a)
        b1.students.addAll([s1])

        def b2 = book("Book B", 110, a)
        b2.students.addAll([s2])

        bookRepository.saveAll([b1, b2])

        when:
        def result = bookRepository.findAll(BookRepository.Specification.withStudentName("Alice"))

        then:
        result.size() == 1
        result*.title == ["Book A"]
    }

    void "pagination + sort by Book_.TITLE works (metamodel constant) and returns stable page"() {
        given:
        def a = author("Author", null); authorRepository.save(a)

        bookRepository.saveAll([
                book("C", 1, a),
                book("A", 1, a),
                book("B", 1, a),
        ])

        when:
        def page = bookRepository.findAll(
                BookRepository.Specification.totalPagesGreaterThan(0),
                Pageable.from(0, 2, Sort.of(Sort.Order.asc(Book_.TITLE)))
        ).content

        then:
        page*.title == ["A", "B"]
    }

    void "can query by singular attributes and enum using static metamodel"() {
        given:
        def c1 = new Client()
        c1.id = 1L
        c1.name = "Alice"
        c1.tier = Client.Tier.PRO
        c1.createdAt = Instant.now()
        c1.billingAddress = new Client.Address("street", "city")

        def c2 = new Client()
        c2.id = 2L
        c2.name = "Bob"
        c2.tier = Client.Tier.BASIC
        c2.createdAt = Instant.now()
        c2.billingAddress = new Client.Address("street", "city")

        clientRepository.saveAll([c1, c2])

        when:
        def result = clientRepository.findAll(
                ClientRepository.Specifications.tierEquals(Client.Tier.PRO)
                        .and(ClientRepository.Specifications.nameEquals("Alice"))
        )

        then:
        result.size() == 1
        result.first().id == 1L
        result.first().name == "Alice"
        result.first().tier == Client.Tier.PRO
    }

    void "can join list relationship using static metamodel"() {
        given:
        populateClientAndCategories()
        when:
        def ids = clientRepository.findAll(ClientRepository.Specifications.withCategoryListName("Sci-Fi"))
                .collect { it.id }
        then:
        ids == [3L]
    }

    void "can join set relationship using static metamodel"() {
        given:
        populateClientAndCategories()
        when:
        def result = clientRepository.findAll(ClientRepository.Specifications.withCategorySetName("History"))
        then:
        !result.isEmpty()
    }

    void "can filter by many-to-one using static metamodel"() {
        given:
        populateClientAndCategories()
        when:
        def result = clientRepository.findAll(ClientRepository.Specifications.mainCategoryNameEquals("Main"))

        then:
        result.size() == 1
        result.first().id == 3
    }

    void "can query by embedded id parts using static metamodel"() {
        given:
        def e1 = new EmployeeMixedAccessEmbeddedId()
        e1.id = new EmployeeId(id: 1L, number: "A")
        e1.name = "Alice"
        e1.salary = 100_000d

        def e2 = new EmployeeMixedAccessEmbeddedId()
        e2.id = new EmployeeId(id: 2L, number: "B")
        e2.name = "Bob"
        e2.salary = 50_000d

        employeeMixedAccessEmbeddedIdRepository.saveAll([e1, e2])

        when:
        def result = employeeMixedAccessEmbeddedIdRepository.findAll(
                EmployeeMixedAccessEmbeddedIdRepository.Specification.embeddedIdEquals(2L, "B")
        )

        then:
        result.size() == 1
        result.first().id.id == 2L
        result.first().id.number == "B"
        result.first().name == "Bob"
        result.first().salary == 50_000d
    }

    void "can query by embedded id id-part only using static metamodel"() {
        given:
        def e1 = new EmployeeMixedAccessEmbeddedId(
                id: new EmployeeId(id: 10L, number: "X"),
                name: "Xavier",
                salary: 1d
        )
        def e2 = new EmployeeMixedAccessEmbeddedId(
                id: new EmployeeId(id: 11L, number: "Y"),
                name: "Yara",
                salary: 2d
        )
        employeeMixedAccessEmbeddedIdRepository.saveAll([e1, e2])

        when:
        def result = employeeMixedAccessEmbeddedIdRepository.findAll(EmployeeMixedAccessEmbeddedIdRepository.Specification.embeddedIdIdEquals(11L))

        then:
        result.size() == 1
        result.first().name == "Yara"
        result.first().id.number == "Y"
    }
    // Ignored because access annotation on fields are not supported currently
    @Ignore
    void "mixed access: fieldAnnotated (FIELD override) is persisted and queryable"() {
        given:
        def e = new EmployeeMixedAccessEmbeddedId()
        e.id = new EmployeeId(id: 100L, number: "ZZ")
        e.name = "Eve"
        e.salary = 90_000d

        // fieldAnnotated has FIELD access override but no accessor.
        // Set reflectively to persist and verify it is queryable.
        setPrivateField(e, "fieldAnnotated", "EDGE")

        employeeMixedAccessEmbeddedIdRepository.save(e)

        when:
        def result = employeeMixedAccessEmbeddedIdRepository.findAll()
//        def result = employeeMixedAccessEmbeddedIdRepository.findAll(EmployeeMixedAccessEmbeddedIdRepository.Specification.fieldAnnotatedEquals("EDGE"))

        then:
        result.size() == 1
        result.first().name == "Eve"
        result.first().id.id == 100L
        result.first().id.number == "ZZ"
    }

    void "can build criteria query using generated static metamodel - filter by string and boxed number"() {
        given:
        def t1 = new Train(
                "Night Express",
                "NE-1",
                500,
                160.5d,
                true,
                LocalDateTime.of(2026, 1, 10, 20, 15),
                Instant.parse("2026-01-01T00:00:00Z")
        )
        def trainSpecs = new TrainSpecs("electric", 10, 2222)
        t1.departureDate = LocalDate.of(2026, 1, 10)
        t1.departureTimeOnly = LocalTime.of(20, 15)
        t1.specs = trainSpecs

        def t2 = new Train(
                "Local Shuttle",
                "LS-9",
                120,
                80.0d,
                false,
                LocalDateTime.of(2026, 1, 11, 9, 0),
                Instant.parse("2026-01-02T00:00:00Z")
        )
        t2.departureDate = LocalDate.of(2026, 1, 11)
        t2.departureTimeOnly = LocalTime.of(9, 0)
        t2.specs = trainSpecs
        trainRepository.saveAll([t1, t2])

        when:
        def result = trainRepository.findAll(TrainRepository.Specification.trainModelEqual("NE-1").and(TrainRepository.Specification.capacityBiggerThan(300)))

        then:
        result.size() == 1
        result.first().name == "Night Express"
        result.first().capacity == 500
    }

    void "can filter by boolean and double using static metamodel"() {
        given:
        def trainSpecs = new TrainSpecs("electric", 10, 2222)

        def t1 = new Train(
                "A", "M1", 100, 90.0d, true,
                LocalDateTime.of(2026, 2, 1, 8, 0),
                Instant.parse("2026-02-01T00:00:00Z")
        )
        t1.departureDate = LocalDate.of(2026, 2, 1)
        t1.departureTimeOnly = LocalTime.of(8, 0)

        def t2 = new Train(
                "B", "M2", 200, 150.0d, false,
                LocalDateTime.of(2026, 2, 2, 8, 0),
                Instant.parse("2026-02-02T00:00:00Z")
        )
        t2.departureDate = LocalDate.of(2026, 2, 2)
        t2.departureTimeOnly = LocalTime.of(8, 0)
        t1.specs = trainSpecs
        t2.specs = trainSpecs

        trainRepository.saveAll([t1, t2])

        when:
        def result = trainRepository.findAll(TrainRepository.Specification.isElectric().and(TrainRepository.Specification.speedLessThan(100.0d)))

        then:
        result.size() == 1
        result.first().model == "M1"
    }

    void "can filter by LocalDateTime using static metamodel and order by departure time"() {
        given:
        def trainSpecs = new TrainSpecs("electric", 10, 2222)

        def early = new Train(
                "Early", "E1", 50, 60.0d, true,
                LocalDateTime.of(2026, 3, 1, 6, 30),
                Instant.parse("2026-03-01T00:00:00Z")
        )
        early.departureDate = LocalDate.of(2026, 3, 1)
        early.departureTimeOnly = LocalTime.of(6, 30)

        def late = new Train(
                "Late", "L1", 50, 60.0d, true,
                LocalDateTime.of(2026, 3, 1, 18, 45),
                Instant.parse("2026-03-01T00:00:01Z")
        )
        late.departureDate = LocalDate.of(2026, 3, 1)
        late.departureTimeOnly = LocalTime.of(18, 45)

        early.specs = trainSpecs
        late.specs = trainSpecs

        trainRepository.saveAll([early, late])

        when:
        def result = trainRepository.findAll(
                TrainRepository.Specification.departureTimeGreaterThan(LocalDateTime.of(2026, 3, 1, 12, 0)),
                Sort.of(Sort.Order.asc(Train_.DEPARTURE_TIME))
        )

        then:
        result.size() == 1
        result.first().name == "Late"
    }

    void "can filter by Instant using static metamodel"() {
        given:
        def trainSpecs = new TrainSpecs("electric", 10, 2222)

        def t1 = new Train(
                "T1", "I1", 1, 1.0d, true,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                Instant.parse("2026-04-01T00:00:00Z")
        )
        t1.departureDate = LocalDate.of(2026, 4, 1)
        t1.departureTimeOnly = LocalTime.of(10, 0)

        def t2 = new Train(
                "T2", "I2", 1, 1.0d, true,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                Instant.parse("2026-04-02T00:00:00Z")
        )
        t2.departureDate = LocalDate.of(2026, 4, 1)
        t2.departureTimeOnly = LocalTime.of(10, 0)

        t1.specs = trainSpecs
        t2.specs = trainSpecs

        trainRepository.saveAll([t1, t2])

        when:
        def result = trainRepository.findAll(TrainRepository.Specification.createdAtGreaterThan(Instant.parse("2026-04-01T12:00:00Z")))

        then:
        result.size() == 1
        result.first().model == "I2"
    }

    void "can filter by LocalDate and LocalTime using static metamodel"() {
        given:
        def trainSpecs = new TrainSpecs("electric", 10, 2222)

        def morning = new Train(
                "Morning", "DT1", 10, 10.0d, true,
                LocalDateTime.of(2026, 5, 10, 9, 15),
                Instant.parse("2026-05-01T00:00:00Z")
        )
        morning.departureDate = LocalDate.of(2026, 5, 10)
        morning.departureTimeOnly = LocalTime.of(9, 15)

        def evening = new Train(
                "Evening", "DT2", 10, 10.0d, true,
                LocalDateTime.of(2026, 5, 10, 18, 0),
                Instant.parse("2026-05-01T00:00:00Z")
        )
        evening.departureDate = LocalDate.of(2026, 5, 10)
        evening.departureTimeOnly = LocalTime.of(18, 0)

        morning.specs = trainSpecs
        evening.specs = trainSpecs

        trainRepository.saveAll([morning, evening])

        when:
        def result = trainRepository.findAll(
                TrainRepository.Specification.departureDateEqual(LocalDate.of(2026, 5, 10))
                        .and(TrainRepository.Specification.departureTimeOnlyGreaterThan(LocalTime.of(12, 0)))
        )

        then:
        result.size() == 1
        result.first().name == "Evening"
    }

    private static void setPrivateField(Object target, String fieldName, Object value) {
        Field f = target.class.getDeclaredField(fieldName)
        f.accessible = true
        f.set(target, value)
    }

    private static Book book(String title, int totalPages, Author author) {
        def b = new Book()
        b.title = title
        b.totalPages = totalPages
        b.author = author
        return b
    }

    private static Author author(String name, String nickName) {
        def a = new Author()
        a.name = name
        a.nickName = nickName
        return a
    }

    private static Genre genre(String genreName) {
        def g = new Genre()
        g.genreName = genreName
        return g
    }

    private static Publisher publisher(String zipCode) {
        def p = new Publisher()
        p.zipCode = zipCode
        return p
    }
}
