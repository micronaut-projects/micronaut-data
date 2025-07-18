package io.micronaut.data.model.jpa.criteria

import io.micronaut.annotation.processing.visitor.JavaClassElement
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.tests.AbstractCriteriaSpec
import io.micronaut.inject.ast.ClassElement
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Subquery
import spock.lang.Unroll

import java.util.function.Function

class CriteriaSpec extends AbstractCriteriaSpec {

    PersistentEntityCriteriaBuilder criteriaBuilder

    SourcePersistentEntityCriteriaQuery criteriaQuery

    SourcePersistentEntityCriteriaDelete criteriaDelete

    SourcePersistentEntityCriteriaUpdate criteriaUpdate

    ClassElement testEntityElement

    Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<ClassElement, SourcePersistentEntity>() {

        private Map<String, SourcePersistentEntity> entityMap = new HashMap<>()

        @Override
        SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), { it ->
                new SourcePersistentEntity(classElement, this)
            })
        }
    }

    void setup() {
        testEntityElement = buildCustomElement()
        criteriaBuilder = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver)
        criteriaQuery = criteriaBuilder.createQuery()
        criteriaDelete = criteriaBuilder.createCriteriaDelete(null)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(null)
    }

    @Override
    PersistentEntityRoot createRoot(Subquery query) {
        return query.from(testEntityElement)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(testEntityElement)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(testEntityElement)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(testEntityElement)
    }

    ClassElement getClassElement(Class<?> clazz) {
        return new CustomAbstractDataSpec().buildClassElement("""class Test {}""", { JavaClassElement el -> el.@visitorContext.getClassElement(clazz).get()})
    }

    void "test subquery IN"() {
        given:
            def bookClassElement = buildBookElement()
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(bookClassElement)
            def subquery = criteriaQuery.subquery(Long)
            def subqueryBookRoot = subquery.from(bookClassElement)
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("id"), 123))
            criteriaQuery.where(
                    bookRoot.<Long>get("id").in(subquery)
            )
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT book_."id",book_."author_id",book_."title",book_."total_pages",book_."last_updated" FROM "book" book_ WHERE (book_."id" IN (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" = 123)))'''
    }

    void "test subquery EQ"() {
        given:
            def bookClassElement = buildBookElement()
            def criteriaQuery = criteriaBuilder.createQuery(Book)
            def bookRoot = criteriaQuery.from(bookClassElement)
            def subquery = criteriaQuery.subquery(Long)
            def subqueryBookRoot = subquery.from(bookClassElement)
            subquery.select(subqueryBookRoot.get("id"))
            subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("id"), 123))
            criteriaQuery.where(
                    criteriaBuilder.equal(bookRoot.<Long>get("id"), subquery)
            )
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT book_."id",book_."author_id",book_."title",book_."total_pages",book_."last_updated" FROM "book" book_ WHERE (book_."id" = (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" = 123)))'''
    }

    void "test function projection 3"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.select(
                    criteriaBuilder.function(
                            "MYFUNC3",
                            String,
                            criteriaBuilder.parameter(String),
                            criteriaBuilder.literal("abc")
                    )
            )
            String query = getSqlQuery(criteriaQuery)

        expect:
            query == '''SELECT MYFUNC3(?,'abc') FROM "test" test_'''
    }

    @Unroll
    void "test criteria predicate"(Specification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            def predicate = specification.toPredicate(entityRoot, criteriaQuery, criteriaBuilder)
            if (predicate) {
                criteriaQuery.where(predicate)
            }
            String whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            specification << [
                    { root, query, cb ->
                        root.get("amount").in(100, 200)
                    } as Specification,
                    { root, query, cb ->
                        root.get("amount").in(100, 200).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get("amount")).value(100).value(200)
                    } as Specification,
                    { root, query, cb ->
                        cb.in(root.get("amount")).value(100).value(200).not()
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get("amount").in([parameter] as Expression<?>[])
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        root.get("amount").in([parameter] as Expression<?>[]).not()
                    } as Specification,
                    { root, query, cb ->
                        cb.between(root.get("enabled"), true, false)
                    } as Specification,
                    { root, query, cb ->
                        def parameter = cb.parameter(Integer)
                        cb.between(root.get("amount"), parameter, parameter)
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        null
                    } as Specification,
                    { root, query, cb ->
                        query.where(root.get("enabled"))
                        query.orderBy(cb.desc(root.get("amount")), cb.asc(root.get("budget")))
                        null
                    } as Specification,
                    { root, query, cb ->
                        def pred1 = cb.or(root.get("enabled"), root.get("enabled2"))
                        def pred2 = cb.or(pred1, cb.equal(root.get("amount"), 100))
                        def andPred = cb.and(cb.equal(root.get("budget"), 200), pred2)
                        andPred
                    } as Specification,
                    { root, query, cb ->
                        cb.equal(cb.lower(cb.upper(root.get("name"))), "Denis")
                    } as Specification,
                    { root, query, cb ->
                        cb.equal(cb.lower(cb.upper(root.get("name"))), cb.lower(cb.literal("Denis")))
                    } as Specification
            ]
            expectedWhereQuery << [
                    '(test_."amount" IN (100,200))',
                    '(test_."amount" NOT IN (100,200))',
                    '(test_."amount" IN (100,200))',
                    '(test_."amount" NOT IN (100,200))',
                    '(test_."amount" IN (?))',
                    '(test_."amount" NOT IN (?))',
                    '((test_."enabled" >= TRUE AND test_."enabled" <= FALSE))',
                    '((test_."amount" >= ? AND test_."amount" <= ?))',
                    '(test_."enabled" = TRUE)',
                    '(test_."enabled" = TRUE) ORDER BY test_."amount" DESC,test_."budget" ASC',
                    '(test_."budget" = 200 AND (test_."enabled" = TRUE OR test_."enabled2" = TRUE OR test_."amount" = 100))',
                    '''(LOWER(UPPER(test_."name")) = 'Denis')''',
                    '''(LOWER(UPPER(test_."name")) = LOWER('Denis'))'''
            ]
    }

    @Unroll
    void "test delete"(DeleteSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaDelete)
            def predicate = specification.toPredicate(entityRoot, criteriaDelete, criteriaBuilder)
            if (predicate) {
                criteriaDelete.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaDelete)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        cb.ge(root.get("amount"), 1000)
                    } as DeleteSpecification,
            ]
            expectedQuery << [
                    'DELETE  FROM "test"  WHERE ("amount" >= 1000)',
            ]
    }

    @Unroll
    void "test update"(UpdateSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaUpdate)
            def predicate = specification.toPredicate(entityRoot, criteriaUpdate, criteriaBuilder)
            if (predicate) {
                criteriaUpdate.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaUpdate)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        query.set("name", "ABC")
                        query.set(root.get("amount"), 123)
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", cb.parameter(String))
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get("amount"), cb.parameter(Integer))
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedQuery << [
                    'UPDATE "test" SET "name"=\'ABC\',"amount"=123 WHERE ("amount" >= 1000)',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= 1000)',
                    'UPDATE "test" SET "name"=\'test\',"amount"=? WHERE ("amount" >= 1000)',
            ]
    }

    @Unroll
    void "test update returning"(UpdateSpecification specification) {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaUpdate)
            def predicate = specification.toPredicate(entityRoot, criteriaUpdate, criteriaBuilder)
            if (predicate) {
                criteriaUpdate.where(predicate)
            }
            String sqlQuery = getSqlQuery(criteriaUpdate, Dialect.POSTGRES)

        expect:
            sqlQuery == expectedQuery

        where:
            specification << [
                    { root, query, cb ->
                        query.set("name", "ABC")
                        Path<Number> amountProperty = root.get("amount")
                        query.set(amountProperty, 123)
                        (query as PersistentEntityCriteriaUpdate).returning(amountProperty)
                        cb.ge(amountProperty, 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", cb.parameter(String))
                        query.set(root.get("amount"), cb.parameter(Integer))
                        (query as PersistentEntityCriteriaUpdate).returning(root)
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
                    { root, query, cb ->
                        query.set("name", "test")
                        query.set(root.get("amount"), cb.parameter(Integer))
                        (query as PersistentEntityCriteriaUpdate).returning(root)
                        cb.ge(root.get("amount"), 1000)
                    } as UpdateSpecification,
            ]
            expectedQuery << [
                    'UPDATE "test" SET "name"=\'ABC\',"amount"=123 WHERE ("amount" >= 1000) RETURNING "amount"',
                    'UPDATE "test" SET "name"=?,"amount"=? WHERE ("amount" >= 1000) RETURNING "id","name","enabled2","enabled","age","amount","budget"',
                    'UPDATE "test" SET "name"=\'test\',"amount"=? WHERE ("amount" >= 1000) RETURNING "id","name","enabled2","enabled","age","amount","budget"',
            ]
    }

    @Unroll
    void "test property value #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateValue(predicate, entityRoot, property1, value))
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '(test_."enabled" = TRUE)'
            "enabled" | true                    | "notEqual"             | '(test_."enabled" != TRUE)'
            "enabled" | true                    | "greaterThan"          | '(test_."enabled" > TRUE)'
            "enabled" | true                    | "greaterThanOrEqualTo" | '(test_."enabled" >= TRUE)'
            "enabled" | true                    | "lessThan"             | '(test_."enabled" < TRUE)'
            "enabled" | true                    | "lessThanOrEqualTo"    | '(test_."enabled" <= TRUE)'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '(test_."amount" > 100)'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '(test_."amount" >= 100)'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '(test_."amount" < 100)'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '(test_."amount" <= 100)'
    }

    @Unroll
    void "test property value not #predicate predicate produces where query: #expectedWhereQuery"() {
        given:
            PersistentEntityRoot entityRoot = createRoot(criteriaQuery)
            criteriaQuery.where(predicateValue(predicate, entityRoot, property1, value).not())
            def whereSqlQuery = getWhereQueryPart(criteriaQuery)

        expect:
            whereSqlQuery == expectedWhereQuery

        where:
            property1 | value                   | predicate              | expectedWhereQuery
            "enabled" | true                    | "equal"                | '(test_."enabled" != TRUE)'
            "enabled" | true                    | "notEqual"             | '(test_."enabled" = TRUE)'
            "enabled" | true                    | "greaterThan"          | '(NOT(test_."enabled" > TRUE))'
            "enabled" | true                    | "greaterThanOrEqualTo" | '(NOT(test_."enabled" >= TRUE))'
            "enabled" | true                    | "lessThan"             | '(NOT(test_."enabled" < TRUE))'
            "enabled" | true                    | "lessThanOrEqualTo"    | '(NOT(test_."enabled" <= TRUE))'
            "amount"  | BigDecimal.valueOf(100) | "gt"                   | '(NOT(test_."amount" > 100))'
            "amount"  | BigDecimal.valueOf(100) | "ge"                   | '(NOT(test_."amount" >= 100))'
            "amount"  | BigDecimal.valueOf(100) | "lt"                   | '(NOT(test_."amount" < 100))'
            "amount"  | BigDecimal.valueOf(100) | "le"                   | '(NOT(test_."amount" <= 100))'
    }

    private ClassElement buildCustomElement() {
        new CustomAbstractDataSpec().buildClassElement("""
package test;
import io.micronaut.data.annotation.*;
import java.math.*;
import java.util.List;

@MappedEntity
class Test {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "test")
    private List<OtherEntity> others;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getEnabled2() {
        return enabled2;
    }

    public void setEnabled2(Boolean enabled2) {
        this.enabled2 = enabled2;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getAge() {
        return age;
    }

    public void setAge(Long age) {
        this.age = age;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public List<OtherEntity> getOthers() {
        return others;
    }

    public void setOthers(List<OtherEntity> others) {
        this.others = others;
    }
}

@MappedEntity
class OtherEntity {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Test test;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private SimpleEntity simple;

    public OtherEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getEnabled2() {
        return enabled2;
    }

    public void setEnabled2(Boolean enabled2) {
        this.enabled2 = enabled2;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getAge() {
        return age;
    }

    public void setAge(Long age) {
        this.age = age;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public Test getTest() {
        return test;
    }

    public void setTest(Test test) {
        this.test = test;
    }

    public SimpleEntity getSimple() {
        return simple;
    }

    public void setSimple(SimpleEntity simple) {
        this.simple = simple;
    }

}

@MappedEntity
class SimpleEntity {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;

    public SimpleEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getEnabled2() {
        return enabled2;
    }

    public void setEnabled2(Boolean enabled2) {
        this.enabled2 = enabled2;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getAge() {
        return age;
    }

    public void setAge(Long age) {
        this.age = age;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

}

""")
    }

    private ClassElement buildBookElement() {
        new CustomAbstractDataSpec().buildClassElement("""
package test;

import io.micronaut.data.annotation.DateUpdated;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private int totalPages;

    @ManyToOne(fetch = FetchType.LAZY)
    private Author author;

    @DateUpdated
    private LocalDateTime lastUpdated;


    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

}

@Entity
class Author {

    @Id
    @GeneratedValue
    private Long id;

    private String name;


    @OneToMany(cascade = CascadeType.ALL)
    private Set<Book> books = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Book> getBooks() {
        return books;
    }

    public void setBooks(Set<Book> books) {
        this.books = books;
    }
}

""")
    }

    static class CustomAbstractDataSpec extends AbstractDataSpec {

        ClassElement buildClassElement(String s) {
            return super.buildClassElement(s)
        }
    }

}
