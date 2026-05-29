/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.Join
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.InsertReturningOneInterceptor
import io.micronaut.data.intercept.UpdateInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.DataType
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.entities.Invoice
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.entity.ActivityPeriodEntity
import io.micronaut.data.processor.entity.SomeEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.jdbc.entities.EmployeeGroup
import jakarta.data.page.PageRequest
import jakarta.data.restrict.Restriction
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.anyParameterExpandable
import static io.micronaut.data.processor.visitors.TestUtils.getCountQuery
import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getJoins
import static io.micronaut.data.processor.visitors.TestUtils.getOperationType
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterExpressions
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterRoles
import static io.micronaut.data.processor.visitors.TestUtils.getQuery
import static io.micronaut.data.processor.visitors.TestUtils.getRawQuery
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType
import static io.micronaut.data.processor.visitors.TestUtils.isExpandableQuery

class BuildQuerySpec extends AbstractDataSpec {

    void "test to-many join on repository type that inherits from CrudRepository"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.*;

@JdbcRepository(dialect= Dialect.MYSQL)
@Join("books")
interface MyInterface extends CrudRepository<Author, Long> {
}
"""
        )

        expect:"The repository to compile"
        repository != null
    }

    void "test POSTGRES quoted syntax"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.CustomBook;

@JdbcRepository(dialect= Dialect.POSTGRES)
interface MyInterface2 extends CrudRepository<CustomBook, Long> {
}
"""
            )

        when:
            String query = getQuery(repository.getRequiredMethod("findById", Long))

        then:
            query == 'SELECT custom_book_."id",custom_book_."title" FROM "CustomBooK" custom_book_ WHERE (custom_book_."id" = ?)'
    }

    void "test POSTGRES custom query"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.CustomBook;

@JdbcRepository(dialect= Dialect.POSTGRES)
interface MyInterface2 extends CrudRepository<CustomBook, Long> {

    @Query("SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]")
    Optional<CustomBook> somethingWithCast(String[] nickNames);

}
"""
            )

            def method = repository.getRequiredMethod("somethingWithCast", String[])
        when:
            String query = getQuery(method)
            String rawQuery = getRawQuery(method)

        then:
            query == 'SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]'
            rawQuery == 'SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[?]'
            getResultDataType(method) == DataType.ENTITY
    }

    @Unroll
    void "test #dialect vector search score projection has a single alias"() {
        given:
            def repository = buildRepository('test.VectorDocRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.persistence.Column;

@MappedEntity
class VectorDoc {
    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 3)
    private FloatVector embedding;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }
}

@JdbcRepository(dialect = Dialect.${dialect})
interface VectorDocRepository extends CrudRepository<VectorDoc, Long> {
    SearchResults<VectorDoc> searchByEmbeddingNear(Vector vector, Double maxDistance);
}
"""
            )

        when:
            String query = getQuery(repository.getRequiredMethod(
                    "searchByEmbeddingNear",
                    Class.forName("io.micronaut.data.model.vector.Vector"),
                    Double
            ))

        then:
            query.contains(' AS mn_score FROM ')
            query.count(' AS mn_score') == 1
            !query.contains(' AS mn_score AS mn_score')

        where:
            dialect << ["POSTGRES", "ORACLE", "MYSQL"]
    }

    @Unroll
    void "test #dialect vector top search orders by vector score"() {
        given:
            def repository = buildRepository('test.VectorDocRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.persistence.Column;

@MappedEntity
class VectorDoc {
    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 3)
    private FloatVector embedding;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }
}

@JdbcRepository(dialect = Dialect.${dialect})
interface VectorDocRepository extends CrudRepository<VectorDoc, Long> {
    SearchResults<VectorDoc> searchTop2ByEmbeddingNear(Vector vector, Double maxDistance);
}
"""
            )

        when:
            String query = getQuery(repository.getRequiredMethod(
                    "searchTop2ByEmbeddingNear",
                    Class.forName("io.micronaut.data.model.vector.Vector"),
                    Double
            ))

        then:
            query.contains(' ORDER BY ')
            query.indexOf(' ORDER BY ') > query.indexOf(' WHERE ')
            query.indexOf(' ORDER BY ') < query.indexOf(limitToken)
            query.contains(scoreToken)

        where:
            dialect    | limitToken       | scoreToken
            "POSTGRES" | " LIMIT 2"       | " <=> "
            "ORACLE"   | "FETCH NEXT 2"   | "VECTOR_DISTANCE("
            "MYSQL"    | " LIMIT 2"       | "DISTANCE("
    }

    void "test vector search score projection uses vector parameter for matching predicate"() {
        given:
            def repository = buildRepository('test.VectorDocRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.persistence.Column;

@MappedEntity
class VectorDoc {
    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 3)
    private FloatVector referenceEmbedding;

    @Column(length = 3)
    private FloatVector embedding;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FloatVector getReferenceEmbedding() {
        return referenceEmbedding;
    }

    public void setReferenceEmbedding(FloatVector referenceEmbedding) {
        this.referenceEmbedding = referenceEmbedding;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface VectorDocRepository extends CrudRepository<VectorDoc, Long> {
    SearchResults<VectorDoc> searchTop2ByReferenceEmbeddingAndEmbeddingNear(Vector reference, Vector query, Double maxDistance);
}
"""
            )

        when:
            def method = repository.getRequiredMethod(
                    "searchTop2ByReferenceEmbeddingAndEmbeddingNear",
                    Class.forName("io.micronaut.data.model.vector.Vector"),
                    Class.forName("io.micronaut.data.model.vector.Vector"),
                    Double
            )

        then:
            getQuery(method).contains(' AS mn_score FROM ')
            getParameterBindingIndexes(method).first() == "1"
            getParameterBindingIndexes(method).last() == "1"
    }

    void "test vector search score projection ignores role parameters when resolving vector predicate"() {
        given:
            def repository = buildRepository('test.VectorDocRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.persistence.Column;

@MappedEntity
class VectorDoc {
    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 3)
    private FloatVector embedding;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface VectorDocRepository extends CrudRepository<VectorDoc, Long> {
    SearchResults<VectorDoc> searchByEmbeddingNear(Sort sort, Vector query, Double maxDistance);
}
"""
            )

        when:
            def method = repository.getRequiredMethod(
                    "searchByEmbeddingNear",
                    Class.forName("io.micronaut.data.model.Sort"),
                    Class.forName("io.micronaut.data.model.vector.Vector"),
                    Double
            )

        then:
            getQuery(method).contains(' AS mn_score FROM ')
            getParameterBindingIndexes(method).first() == "1"
            getParameterBindingIndexes(method)[1] == "1"
    }

    void "test POSTGRES custom query - expression"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.CustomBook;

@JdbcRepository(dialect= Dialect.POSTGRES)
interface MyInterface2 extends CrudRepository<CustomBook, Long> {

    @Query("SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]")
    @ParameterExpression(name = "nickNames", expression = "#{this.getNicknames()}")
    Optional<CustomBook> somethingWithCast();

    default String[] getNicknames() {
        return new String[]{"Abc"};
    }

}
"""
            )

            def method = repository.getRequiredMethod("somethingWithCast")
        when:
            String query = getQuery(method)
            String rawQuery = getRawQuery(method)

        then:
            query == 'SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]'
            rawQuery == 'SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[?]'
            getResultDataType(method) == DataType.ENTITY
            getParameterExpressions(method) == [true] as Boolean[]
    }

    void "test invalid expression"() {
        when:
            buildRepository('test.MyInterface2', """
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.CustomBook;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface MyInterface2 extends CrudRepository<CustomBook, Long> {

    @Query("SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]")
    @ParameterExpression(name = "nickNames", expression = "this.getNicknames()")
    Optional<CustomBook> somethingWithCast();

    default String[] getNicknames() {
        return new String[]{"Abc"};
    }

}
"""
            )
        then:
            def e = thrown(Exception)
            e.message.contains "Expected an expression '#{...}' found a string!"
    }

    @PendingFeature
    void "test POSTGRES custom query - expression class"() {
        given:
            def repository = buildRepository('test.MyInterface2', """
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.CustomBook;

@ParameterExpression(name = "nickNames", expression = "#{this.getNicknames()}")
@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface MyInterface2 extends CrudRepository<CustomBook, Long> {

    @Query("SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]")
    Optional<CustomBook> somethingWithCast();

    default String[] getNicknames() {
        return new String[]{"Abc"};
    }

}
"""
            )

            def method = repository.getRequiredMethod("somethingWithCast")
        when:
            String query = getQuery(method)
            String rawQuery = getRawQuery(method)

        then:
            query == 'SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[:nickNames]'
            rawQuery == 'SELECT * FROM arrays_entity WHERE stringArray::varchar[] && ARRAY[?]'
            getResultDataType(method) == DataType.ENTITY
            getParameterExpressions(method) == [true] as Boolean[]
    }

    void "test join on repository type that inherits from CrudRepository"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.processor.sql.AliasAuthor;
import io.micronaut.data.processor.sql.AliasBook;

@JdbcRepository(dialect= Dialect.MYSQL)
@Join("author")
@io.micronaut.context.annotation.Executable
interface MyInterface extends CrudRepository<AliasBook, Long> {

    AliasAuthor findAuthorById(@Id Long id);

    @Join("author")
    AliasBook findAliasBookById(@Id Long id);

    @Join("coAuthor")
    AliasBook findOneById(@Id Long id);

    @Join("coAuthor")
    @Join("coAuthor.otherBooks")
    AliasBook findOne(Long id);

    @Join("author.otherBooks")
    AliasBook findAliasBook(Long id);
}
"""
        )

        String query = getQuery(repository.getRequiredMethod(method, Long))

        expect:
        query == sql

        where:
        method                  | sql
        'findAuthorById'        | 'SELECT au.`id`,au.`name`,au.`nick_name` FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` WHERE (alias_book_.`id` = ?)'
        'findAliasBookById'     | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,au.`name` AS auname,au.`nick_name` AS aunick_name FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` WHERE (alias_book_.`id` = ?)'
        'findOneById'           | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,alias_book_co_author_.`name` AS co_author_name,alias_book_co_author_.`nick_name` AS co_author_nick_name,au.`name` AS auname,au.`nick_name` AS aunick_name FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` INNER JOIN `alias_author` alias_book_co_author_ ON alias_book_.`co_author_id`=alias_book_co_author_.`id` WHERE (alias_book_.`id` = ?)'
        'findOne'               | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,alias_book_co_author_ob.`id` AS co_author_obid,alias_book_co_author_ob.`title` AS co_author_obtitle,alias_book_co_author_ob.`total_pages` AS co_author_obtotal_pages,alias_book_co_author_ob.`last_updated` AS co_author_oblast_updated,alias_book_co_author_ob.`author_id` AS co_author_obauthor_id,alias_book_co_author_ob.`co_author_id` AS co_author_obco_author_id,alias_book_co_author_.`name` AS co_author_name,alias_book_co_author_.`nick_name` AS co_author_nick_name,au.`name` AS auname,au.`nick_name` AS aunick_name FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` INNER JOIN `alias_author` alias_book_co_author_ ON alias_book_.`co_author_id`=alias_book_co_author_.`id` INNER JOIN `alias_book` alias_book_co_author_ob ON alias_book_co_author_.`id`=alias_book_co_author_ob.`author_id` WHERE (alias_book_.`id` = ?)'
        'findAliasBook'         | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,au_ob.`id` AS au_obid,au_ob.`title` AS au_obtitle,au_ob.`total_pages` AS au_obtotal_pages,au_ob.`last_updated` AS au_oblast_updated,au_ob.`author_id` AS au_obauthor_id,au_ob.`co_author_id` AS au_obco_author_id,au.`name` AS auname,au.`nick_name` AS aunick_name FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` INNER JOIN `alias_book` au_ob ON au.`id`=au_ob.`author_id` WHERE (alias_book_.`id` = ?)'
    }

    void "test join on repository type that inherits from CrudRepository 2"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.processor.sql.AliasAuthor;
import io.micronaut.data.processor.sql.AliasBook;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect= Dialect.MYSQL)
@Join("author")
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<AliasBook, Long> {

    @Join("coAuthor")
    @Join("coAuthor.otherBooks")
    AliasBook findOne(Long id);
}
"""
        )

        String query = getQuery(repository.getRequiredMethod(method, Long))

        expect:
        query == sql

        where:
        method                  | sql
        'findOne'               | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,alias_book_co_author_ob.`id` AS co_author_obid,alias_book_co_author_ob.`title` AS co_author_obtitle,alias_book_co_author_ob.`total_pages` AS co_author_obtotal_pages,alias_book_co_author_ob.`last_updated` AS co_author_oblast_updated,alias_book_co_author_ob.`author_id` AS co_author_obauthor_id,alias_book_co_author_ob.`co_author_id` AS co_author_obco_author_id,alias_book_co_author_.`name` AS co_author_name,alias_book_co_author_.`nick_name` AS co_author_nick_name,au.`name` AS auname,au.`nick_name` AS aunick_name FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` INNER JOIN `alias_author` alias_book_co_author_ ON alias_book_.`co_author_id`=alias_book_co_author_.`id` INNER JOIN `alias_book` alias_book_co_author_ob ON alias_book_co_author_.`id`=alias_book_co_author_ob.`author_id` WHERE (alias_book_.`id` = ?)'
    }

      void "test to-one join on repository type that inherits from CrudRepository"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.MYSQL)
@Join("author")
@io.micronaut.context.annotation.Executable
interface MyInterface extends CrudRepository<Book, Long> {

    Author findAuthorById(@Id Long id);

    Book findByTitleOrAuthorAndId(String title, Author author, Long id);
}
"""
        )

        when:
        String query1 = getQuery(repository.getRequiredMethod("findAuthorById", Long))
        String query2 = getQuery(repository.getRequiredMethod("findByTitleOrAuthorAndId", String, Author, Long))

        then:
        query1 == 'SELECT book_author_.`id`,book_author_.`name`,book_author_.`nick_name` FROM `book` book_ INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` = ?)'
        query2.endsWith('WHERE ((book_.`title` = ? OR book_.`author_id` = ?) AND book_.`id` = ?)')
    }

    void "test join query on collection with custom ID name"() {
        given:
        def repository = buildRepository('test.MealRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Meal;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MealRepository extends CrudRepository<Meal, Long> {
    @Join("foods")
    Meal searchById(Long id);
}
""")

        def query = getQuery(repository.getRequiredMethod("searchById", Long))

        expect:"The query contains the correct join"
        query.contains('INNER JOIN `food` meal_foods_ ON meal_.`mid`=meal_foods_.`fk_meal_id` AND meal_foods_.fresh = \'Y\' WHERE (meal_.`mid` = ? AND meal_.actual = \'Y\')')

    }

    void "test join query with custom foreign key"() {
        given:
        def repository = buildRepository('test.FoodRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Food;
import java.util.Optional;
import java.util.UUID;

@Repository(value = "secondary")
@JdbcRepository(dialect = Dialect.MYSQL)
interface FoodRepository extends GenericRepository<Food, UUID> {

    @Join("meal")
    Optional<Food> queryById(UUID uuid);

    // Without join to meal
    Optional<Food> findById(UUID uuid);
}
""")

        def query = getQuery(repository.getRequiredMethod("queryById", UUID))
        def queryFind = getQuery(repository.getRequiredMethod("findById", UUID))

        expect:
        query == 'SELECT food_.`fid`,food_.`key`,food_.`carbohydrates`,food_.`portion_grams`,food_.`created_on`,food_.`updated_on`,food_.`fk_meal_id`,food_.`fk_alt_meal`,food_.`loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name` AS ln,food_.`fresh`,food_meal_.`current_blood_glucose` AS meal_current_blood_glucose,food_meal_.`created_on` AS meal_created_on,food_meal_.`updated_on` AS meal_updated_on,food_meal_.`actual` AS meal_actual FROM `food` food_ INNER JOIN `meal` food_meal_ ON food_.`fk_meal_id`=food_meal_.`mid` AND food_meal_.actual = \'Y\' WHERE (food_.`fid` = ? AND food_.fresh = \'Y\')'
        queryFind == 'SELECT food_.`fid`,food_.`key`,food_.`carbohydrates`,food_.`portion_grams`,food_.`created_on`,food_.`updated_on`,food_.`fk_meal_id`,food_.`fk_alt_meal`,food_.`loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name` AS ln,food_.`fresh` FROM `food` food_ WHERE (food_.`fid` = ? AND food_.fresh = \'Y\')'
    }

    void "test query with an entity with custom id and id field"() {
        given:
        def repository = buildRepository('test.CustomIdEntityRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.CustomIdEntity;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CustomIdEntityRepository extends CrudRepository<CustomIdEntity, Long> {

    Optional<CustomIdEntity> findByCustomId(Long customId);

    boolean existsByCustomId(Long customId);

    void deleteByCustomId(Long customId);
}
""")
        when:
        def q = getQuery(repository.getRequiredMethod(method, Long))

        then:
        q == query

        where:
        method             | query
        "existsByCustomId" | 'SELECT TRUE FROM `custom_id_entity` custom_id_entity_ WHERE (custom_id_entity_.`custom_id` = ?)'
        "deleteByCustomId" | 'DELETE  FROM `custom_id_entity`  WHERE (`custom_id` = ?)'
        "findByCustomId"   | 'SELECT custom_id_entity_.`custom_id`,custom_id_entity_.`id`,custom_id_entity_.`name` FROM `custom_id_entity` custom_id_entity_ WHERE (custom_id_entity_.`custom_id` = ?)'
        "existsById"       | 'SELECT TRUE FROM `custom_id_entity` custom_id_entity_ WHERE (custom_id_entity_.`custom_id` = ?)'
        "findById"         | 'SELECT custom_id_entity_.`custom_id`,custom_id_entity_.`id`,custom_id_entity_.`name` FROM `custom_id_entity` custom_id_entity_ WHERE (custom_id_entity_.`custom_id` = ?)'

    }

    @Unroll
    void "test build query with datasource set"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Integer> {
    Optional<Movie> findByTitle(String title);
    Optional<String> findTheLongNameById(int id);
    Optional<Movie> findByTheLongName(String theLongName);
}

${entity('Movie', [title: String, theLongName: String])}
""")
        def method = repository.getRequiredMethod(methodName, arguments)

        expect:
        getQuery(method) == query

        where:
        methodName               | arguments              | query
        'findByTitle'            |String.class            |'SELECT movie_.`id`,movie_.`title`,movie_.`the_long_name` FROM `movie` movie_ WHERE (movie_.`title` = ?)'
        'findTheLongNameById'    |int.class               |'SELECT movie_.`the_long_name` FROM `movie` movie_ WHERE (movie_.`id` = ?)'
        'findByTheLongName'      |String.class            |'SELECT movie_.`id`,movie_.`title`,movie_.`the_long_name` FROM `movie` movie_ WHERE (movie_.`the_long_name` = ?)'
    }

    void "test build DTO projection with pageable"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Integer> {
    Page<MovieTitle> queryAll(Pageable pageable);
}

${entity('Movie', [title: String])}
${dto('MovieTitle', [title: String])}

""")
        def method = repository.getRequiredMethod("queryAll", Pageable)
        def query = getQuery(method)


        expect:
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
        query == 'SELECT movie_.`title` FROM `movie` movie_'

    }

    @Issue('#375')
    void "test in query with property that starts with in"() {
        given:
        def repository = buildRepository('test.SomeEntityRepository', """
@Repository
interface SomeEntityRepository extends CrudRepository<SomeEntity, Long> {
    List<SomeEntity> findByInternetNumberInList(List<Long> internetNumbers);
}

${entity('SomeEntity', [internetNumber: Long])}
""")

        expect:
        repository != null
    }

    void "test multiple join query"() {
        given:
            def repository = buildRepository('test.MealRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Meal;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MealRepository extends CrudRepository<Meal, Long> {
    int countDistinctByFoodsAlternativeMealCurrentBloodGlucoseInList(List<Integer> list);
}
""")

        when:
            def countMethod = repository.getRequiredMethod("countDistinctByFoodsAlternativeMealCurrentBloodGlucoseInList", List)

        then:
            getQuery(countMethod) == 'SELECT COUNT(DISTINCT(meal_.`mid`)) FROM `meal` meal_ INNER JOIN `food` meal_foods_ ON meal_.`mid`=meal_foods_.`fk_meal_id` AND meal_foods_.fresh = \'Y\' INNER JOIN `meal` meal_foods_alternative_meal_ ON meal_foods_.`fk_alt_meal`=meal_foods_alternative_meal_.`mid` AND meal_foods_alternative_meal_.actual = \'Y\' WHERE (meal_foods_alternative_meal_.`current_blood_glucose` IN (?) AND meal_.actual = \'Y\')'
            isExpandableQuery(countMethod)
            anyParameterExpandable(countMethod)

        when:
            def saveMethod = repository.findPossibleMethods("save").findAny().get()
        then:
            !isExpandableQuery(saveMethod)
            !anyParameterExpandable(saveMethod)

        when:
            def updateMethod = repository.findPossibleMethods("update").findAny().get()
        then:
            !isExpandableQuery(updateMethod)
            !anyParameterExpandable(updateMethod)
    }

    void "test find by relation"() {
        given:
            def repository = withEmbeddedNamingStrategy("LEGACY") {
                buildRepository('test.UserRoleRepository', """
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@JdbcRepository(dialect= Dialect.MYSQL)
interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {

    @Join("role")
    Iterable<Role> findRoleByUser(User user);
}

@Entity
@Table(name = "user_role_composite")
class UserRole {
    @EmbeddedId
    private UserRoleId id;

    public UserRoleId getId() {
        return id;
    }

    public void setId(UserRoleId id) {
        this.id = id;
    }
}

@Embeddable
class UserRoleId {
    @ManyToOne
    private User user;
    @ManyToOne
    private Role role;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}

@Entity
@Table(name = "user_composite")
class User {
    @Id
    private Long id;
    private String name;

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
}

@Entity
@Table(name = "role_composite")
class Role {
    @Id
    private Long id;
    private String name;

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
}
""")
            }

            def method = repository.findPossibleMethods("findRoleByUser").findAny().get()
            def query = getQuery(method)

        expect:
            query == 'SELECT user_role_id_role_.`id`,user_role_id_role_.`name` FROM `user_role_composite` user_role_ INNER JOIN `role_composite` user_role_id_role_ ON user_role_.`id_role_id`=user_role_id_role_.`id` WHERE (user_role_.`id_user_id` = ?)'
            getParameterBindingIndexes(method) == ["0"] as String[]
            getParameterBindingPaths(method) == ["id"] as String[]
            getParameterPropertyPaths(method) == ["id.user.id"] as String[]
            getDataTypes(method) == [DataType.LONG]
    }

    void "test multiple join query by identity"() {
        given:
            def repository = buildRepository('test.CitiesRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;import io.micronaut.data.tck.entities.City;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CitiesRepository extends GenericRepository<City, Long> {

    @Join("countryRegion")
    @Join("countryRegion.country")
    int countDistinctByCountryRegionCountryUuid(UUID id);
}
""")
        def query = getQuery(repository.getRequiredMethod("countDistinctByCountryRegionCountryUuid", UUID))

        expect:
        // Extra JOIN is not needed in this case but is added because user defined it
        query == 'SELECT COUNT(DISTINCT(city_.`id`)) FROM `T_CITY` city_ INNER JOIN `CountryRegion` city_country_region_ ON city_.`country_region_id`=city_country_region_.`id` INNER JOIN `country` city_country_region_country_ ON city_country_region_.`countryId`=city_country_region_country_.`uuid` WHERE (city_country_region_.`countryId` = ?)'

    }

    void "test multiple join query by identity 2"() {
        given:
            def repository = buildRepository('test.CitiesRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.City;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CitiesRepository extends CrudRepository<City, Long> {

    @Join("countryRegion")
    int countDistinctByCountryRegionCountryUuid(UUID id);
}
""")

            def method = repository.getRequiredMethod("countDistinctByCountryRegionCountryUuid", UUID)
            def query = getQuery(method)
            def op = getOperationType(method)

        expect:
        query == 'SELECT COUNT(DISTINCT(city_.`id`)) FROM `T_CITY` city_ INNER JOIN `CountryRegion` city_country_region_ ON city_.`country_region_id`=city_country_region_.`id` WHERE (city_country_region_.`countryId` = ?)'
        op == DataMethod.OperationType.COUNT

    }

    void "test join by foreign key and selecting by joined entity identity"() {
        given:
        def repository = buildRepository('test.FacesRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Face;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface FacesRepository extends CrudRepository<Face, Long> {

    @Join("nose")
    int countDistinctByNoseId(Long id);
}
""")
        def query = getQuery(repository.getRequiredMethod("countDistinctByNoseId", Long))

        expect:
        query == 'SELECT COUNT(DISTINCT(face_.`id`)) FROM `face` face_ INNER JOIN `nose` face_nose_ ON face_.`id`=face_nose_.`face_id` WHERE (face_nose_.`id` = ?)'

    }

    void "test native query with colon used in query"() {
        given:
        def repository = buildRepository('test.FacesRepository', """
import org.jspecify.annotations.Nullable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Face;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.ORACLE)
interface FacesRepository extends GenericRepository<Face, Long> {

    @Query("SELECT * FROM face f WHERE" +
           " (f.date_created >= COALESCE(TO_TIMESTAMP(:dateCreated, 'YYYY-MM-DD\\"T\\"HH24\\\\:MI\\\\:SS\\"Z\\"'), f.date_created) OR :dateCreated IS NULL) AND"
           + " (f.name = :name OR :name IS NULL)")
    List<Face> findAllWithOptionalFilters(
        @Nullable String name,
        @Nullable String dateCreated);
}
""")
        def method = repository.getRequiredMethod("findAllWithOptionalFilters", String, String)
        def rawQuery = getRawQuery(method)
        def query = getQuery(method)

        expect:
        rawQuery == 'SELECT * FROM face f WHERE (f.date_created >= COALESCE(TO_TIMESTAMP(?, \'YYYY-MM-DD"T"HH24:MI:SS"Z"\'), f.date_created) OR ? IS NULL) AND (f.name = ? OR ? IS NULL)'
        query == 'SELECT * FROM face f WHERE (f.date_created >= COALESCE(TO_TIMESTAMP(:dateCreated, \'YYYY-MM-DD"T"HH24\\:MI\\:SS"Z"\'), f.date_created) OR :dateCreated IS NULL) AND (f.name = :name OR :name IS NULL)'
    }

    void "test In in properties"() {
        given:
        def repository = buildRepository('test.PurchaseRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.entities.Purchase;
import io.micronaut.data.model.entities.Invoice;
import io.micronaut.data.tck.entities.Face;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PurchaseRepository extends CrudRepository<Purchase, Long> {

    @Join("invoice")
    Purchase findByNameAndInvoiceId(String n, Long id);

    Purchase findByCustomerIdAndShouldReceiveCopyOfInvoiceTrue(Long id, Boolean should);

    Purchase findByCustomerIdOrInvoiceAndShouldReceiveCopyOfInvoiceTrue(Long id, Invoice invoice);
}
""")
        def query1 = getQuery(repository.getRequiredMethod("findByNameAndInvoiceId", String, Long))
        def query2 = getQuery(repository.getRequiredMethod("findByCustomerIdAndShouldReceiveCopyOfInvoiceTrue", Long, Boolean))
        def query3 = getQuery(repository.getRequiredMethod("findByCustomerIdOrInvoiceAndShouldReceiveCopyOfInvoiceTrue", Long, Invoice))

        expect:
        query1 == 'SELECT purchase_.`id`,purchase_.`version`,purchase_.`name`,purchase_.`invoice_id`,purchase_.`customer_id`,purchase_.`should_receive_copy_of_invoice`,purchase_invoice_.`version` AS invoice_version,purchase_invoice_.`name` AS invoice_name FROM `purchase` purchase_ INNER JOIN `invoice` purchase_invoice_ ON purchase_.`invoice_id`=purchase_invoice_.`id` WHERE (purchase_.`name` = ? AND purchase_.`invoice_id` = ?)'
        query2 == 'SELECT purchase_.`id`,purchase_.`version`,purchase_.`name`,purchase_.`invoice_id`,purchase_.`customer_id`,purchase_.`should_receive_copy_of_invoice` FROM `purchase` purchase_ WHERE (purchase_.`customer_id` = ? AND purchase_.`should_receive_copy_of_invoice` = TRUE)'
        query3.endsWith('WHERE ((purchase_.`customer_id` = ? OR purchase_.`invoice_id` = ?) AND purchase_.`should_receive_copy_of_invoice` = TRUE)')

    }

    void "test query using InRange"() {
        given:
        def repository = buildRepository('test.MealRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Meal;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MealRepository extends CrudRepository<Meal, Long> {

    Meal findByCurrentBloodGlucoseInRange(int from, int to);
}
""")

        def query = getQuery(repository.getRequiredMethod("findByCurrentBloodGlucoseInRange", int, int))

        expect:"The query contains the correct where clause for InRange (same as Between)"
        query.contains('WHERE ((meal_.`current_blood_glucose` >= ? AND meal_.`current_blood_glucose` <= ?) AND meal_.actual = \'Y\')')

    }

    void "test build DTO repo with MappedProperty alias"() {
        given:
        def repository = buildRepository('test.ProductDtoRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Product;
import io.micronaut.data.tck.entities.ProductDto;

@JdbcRepository(dialect = Dialect.MYSQL)
interface ProductDtoRepository extends GenericRepository<Product, Long> {

    List<ProductDto> findByNameLike(String name);
}

""")
        def method = repository.getRequiredMethod("findByNameLike", String)
        def query = getQuery(method)


        expect:
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
        query == 'SELECT product_.`name`,product_.`price`,product_.`loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name` AS long_name,product_.`date_created`,product_.`last_updated` FROM `product` product_ WHERE (product_.`name` LIKE ?)'
    }

    void "test build restriction DTO Jakarta Data"() {
        given:
        def repository = buildRepository('test.TrainRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.entities.TrainNameCapacityDto;
import jakarta.data.repository.Find;
import jakarta.data.repository.First;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Select;
import jakarta.data.restrict.Restriction;

@JdbcRepository(dialect = Dialect.MYSQL)
interface TrainRepository extends GenericRepository<Train, Long> {

    @Find
    @OrderBy("capacity")
    @First(3)
    @Select("name")
    @Select("capacity")
    List<TrainNameCapacityDto> find(Restriction<Train> restriction);

}
""")
        def method = repository.getRequiredMethod("find", Restriction)


        expect:
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
        method.stringValue(DataMethod, DataMethod.META_MEMBER_RESULT_TYPE).get() == "io.micronaut.data.tck.entities.TrainNameCapacityDto"
        method.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor"
    }

    void "test build restriction page DTO Jakarta Data"() {
        given:
        def repository = buildRepository('test.TrainRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.entities.TrainNameCapacityDto;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.First;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Select;
import jakarta.data.restrict.Restriction;

@JdbcRepository(dialect = Dialect.MYSQL)
interface TrainRepository extends GenericRepository<Train, Long> {

    @Find
    @OrderBy("capacity")
    @First(3)
    @Select("name")
    @Select("capacity")
    Page<TrainNameCapacityDto> find(Restriction<Train> restriction, PageRequest pageRequest);

}
""")
        def method = repository.getRequiredMethod("find", Restriction, PageRequest)


        expect:
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
        method.stringValue(DataMethod, DataMethod.META_MEMBER_RESULT_TYPE).get() == "io.micronaut.data.tck.entities.TrainNameCapacityDto"
        method.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.FindPageSpecificationInterceptor"
    }

    void "test build restriction async page DTO Jakarta Data"() {
        given:
        def repository = buildRepository('test.TrainRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.entities.TrainNameCapacityDto;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Find;
import jakarta.data.repository.First;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Select;
import jakarta.data.restrict.Restriction;
import java.util.concurrent.CompletionStage;

@JdbcRepository(dialect = Dialect.MYSQL)
interface TrainRepository extends GenericRepository<Train, Long> {

    @Find
    @OrderBy("capacity")
    @First(3)
    @Select("name")
    @Select("capacity")
    CompletionStage<Page<TrainNameCapacityDto>> find(Restriction<Train> restriction, PageRequest pageRequest);

}
""")
        def method = repository.getRequiredMethod("find", Restriction, PageRequest)


        expect:
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
        method.stringValue(DataMethod, DataMethod.META_MEMBER_RESULT_TYPE).get() == "io.micronaut.data.tck.entities.TrainNameCapacityDto"
        method.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.async.FindPageAsyncSpecificationInterceptor"
    }

    void "test join query in repo via mapped entity"() {
        given:
        def repository = buildRepository('test.PageRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Page;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Chapter;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.H2)
interface PageRepository extends GenericRepository<Page, Long> {

    @Join(value = "book.chapters", type = Join.Type.LEFT_FETCH)
    Optional<Book> findBookById(Long id);

    List<Chapter> findBookChaptersById(Long id);

    @Join(value = "book.chapters.book", type = Join.Type.FETCH)
    List<Chapter> findBookChaptersByIdAndNum(Long id, long num);
}
""")

        def method = repository.getRequiredMethod("findBookById", Long)
        def query = getQuery(method)
        def joins = getJoins(method)
        def chaptersQuery = getQuery(repository.getRequiredMethod("findBookChaptersById", Long))
        def chaptersBookJoinMethod = repository.getRequiredMethod("findBookChaptersByIdAndNum", Long, long)
        def chaptersBookJoinQuery = getQuery(chaptersBookJoinMethod)
        def chaptersBookJoinJoins = getJoins(chaptersBookJoinMethod)

        expect:
        query == "SELECT page_book_.`id`,page_book_.`author_id`,page_book_.`genre_id`,page_book_.`title`,page_book_.`total_pages`,page_book_.`publisher_id`,page_book_.`last_updated`,page_book_chapters_.`id` AS chapters_id,page_book_chapters_.`pages` AS chapters_pages,page_book_chapters_.`book_id` AS chapters_book_id,page_book_chapters_.`title` AS chapters_title FROM `page` page_ LEFT JOIN `book` page_book_ ON page_.`book_id`=page_book_.`id` LEFT JOIN `chapter` page_book_chapters_ ON page_book_.`id`=page_book_chapters_.`book_id` WHERE (page_.`id` = ?)"
        joins.size() == 1
        joins.keySet() == ["chapters"] as Set
        chaptersQuery == "SELECT page_book_chapters_.`id`,page_book_chapters_.`pages`,page_book_chapters_.`book_id`,page_book_chapters_.`title` FROM `page` page_ INNER JOIN `book` page_book_ ON page_.`book_id`=page_book_.`id` INNER JOIN `chapter` page_book_chapters_ ON page_book_.`id`=page_book_chapters_.`book_id` WHERE (page_.`id` = ?)"
        chaptersBookJoinQuery == "SELECT page_book_chapters_.`id`,page_book_chapters_.`pages`,page_book_chapters_.`book_id`,page_book_chapters_.`title`,page_book_chapters_book_.`author_id` AS book_author_id,page_book_chapters_book_.`genre_id` AS book_genre_id,page_book_chapters_book_.`title` AS book_title,page_book_chapters_book_.`total_pages` AS book_total_pages,page_book_chapters_book_.`publisher_id` AS book_publisher_id,page_book_chapters_book_.`last_updated` AS book_last_updated FROM `page` page_ INNER JOIN `book` page_book_ ON page_.`book_id`=page_book_.`id` INNER JOIN `chapter` page_book_chapters_ ON page_book_.`id`=page_book_chapters_.`book_id` INNER JOIN `book` page_book_chapters_book_ ON page_book_chapters_.`book_id`=page_book_chapters_book_.`id` WHERE (page_.`id` = ? AND page_.`num` = ?)"
        chaptersBookJoinJoins.size() == 1
        chaptersBookJoinJoins.keySet() == ["book"] as Set
        getResultDataType(method) == DataType.ENTITY
    }

    void "test many-to-many using mappedBy"() {
        given:
        def repository = buildRepository('test.StudentRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.entities.Student;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository extends GenericRepository<Student, Long> {

    @Join(value = "books", type = Join.Type.FETCH)
    Optional<Student> findByName(String name);
}

""")

        def method = repository.getRequiredMethod("findByName", String)
        def query = getQuery(method)

        expect:
        query.contains("FROM `student` student_ INNER JOIN `book_student`")
        getResultDataType(method) == DataType.ENTITY
    }

    void "test many-to-many from inverse association"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends GenericRepository<Book, Long> {

    @Join(value = "students", type = Join.Type.FETCH)
    Optional<Book> findByTitle(String title);
}

""")

        def method = repository.getRequiredMethod("findByTitle", String)
        def query = getQuery(method)

        expect:
        query.contains("FROM `book` book_ INNER JOIN `book_student`")
        getResultDataType(method) == DataType.ENTITY

    }

    void "test unsupported ArrayContains operation"() {
        when:
        buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.MYSQL)
interface BookRepository extends GenericRepository<Book, Long> {

    Optional<Book> findByTitleArrayContains(String title);
}

""")
        then:
        Throwable ex = thrown()
        ex.message.contains('ArrayContains is not supported by this implementation')
    }

    void "test repo for MappedProperty with Embedded"() {
        given:
        def repository = buildRepository('test.RestaurantRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Address;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface RestaurantRepository extends GenericRepository<Restaurant, Long> {

    Optional<Restaurant> findByName(String name);

    Restaurant save(Restaurant entity);

    Restaurant findByAddressStreet(String street);

    Address findAddressById(Long id);

    Optional<Address> findHqAddressById(Long id);

    String getMaxAddressStreetByName(String name);
}

""")

        def findByNameQuery = getQuery(repository.getRequiredMethod("findByName", String))
        def saveQuery = getQuery(repository.getRequiredMethod("save", Restaurant))
        def findByAddressStreetQuery = getQuery(repository.getRequiredMethod("findByAddressStreet", String))
        def findAddressByIdMethod = repository.getRequiredMethod("findAddressById", Long)
        def findAddressByIdQuery = getQuery(findAddressByIdMethod)
        def findHqAddressByIdMethod = repository.getRequiredMethod("findHqAddressById", Long)
        def findHqAddressByIdQuery = getQuery(findHqAddressByIdMethod)
        def getMaxAddressStreetByNameQuery = getQuery(repository.getRequiredMethod("getMaxAddressStreetByName", String))
        expect:
        findByNameQuery == 'SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`street`,restaurant_.`zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM `restaurant` restaurant_ WHERE (restaurant_.`name` = ?)'
        saveQuery == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
        findByAddressStreetQuery == 'SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`street`,restaurant_.`zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM `restaurant` restaurant_ WHERE (restaurant_.`street` = ?)'
        findAddressByIdQuery == 'SELECT restaurant_.`street`,restaurant_.`zip_code` FROM `restaurant` restaurant_ WHERE (restaurant_.`id` = ?)'
        findHqAddressByIdQuery == 'SELECT restaurant_.`hqaddress_street` AS street,restaurant_.`hqaddress_zip_code` AS zip_code FROM `restaurant` restaurant_ WHERE (restaurant_.`id` = ?)'
        getMaxAddressStreetByNameQuery == 'SELECT MAX(restaurant_.`street`) FROM `restaurant` restaurant_ WHERE (restaurant_.`name` = ?)'
        getResultDataType(findAddressByIdMethod) == DataType.ENTITY
        getResultDataType(findHqAddressByIdMethod) == DataType.ENTITY
    }

    void "test embedded projection aliases nested embedded columns"() {
        given:
        def repository = buildRepository('test.VehicleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Jurisdiction;
import io.micronaut.data.tck.entities.Registration;
import io.micronaut.data.tck.entities.Vehicle;

@JdbcRepository(dialect = Dialect.H2)
interface VehicleRepository extends GenericRepository<Vehicle, Long> {

    Registration findFirstRegistrationById(Long id);

    Registration findSecondRegistrationById(Long id);

    Jurisdiction findFirstRegistrationJurisdictionById(Long id);

    Jurisdiction findSecondRegistrationJurisdictionById(Long id);
}

""")

        def findFirstRegistrationByIdQuery = getQuery(repository.getRequiredMethod("findFirstRegistrationById", Long))
        def findSecondRegistrationByIdQuery = getQuery(repository.getRequiredMethod("findSecondRegistrationById", Long))
        def findFirstRegistrationJurisdictionByIdQuery = getQuery(repository.getRequiredMethod("findFirstRegistrationJurisdictionById", Long))
        def findSecondRegistrationJurisdictionByIdQuery = getQuery(repository.getRequiredMethod("findSecondRegistrationJurisdictionById", Long))

        expect:
        findFirstRegistrationByIdQuery == 'SELECT vehicle_.`plate_number`,vehicle_.`status`,vehicle_.`jurisdiction_country_code`,vehicle_.`jurisdiction_region_code` FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        findSecondRegistrationByIdQuery == 'SELECT vehicle_.`second_plate_number` AS plate_number,vehicle_.`second_status` AS status,vehicle_.`second_jurisdiction_country_code` AS jurisdiction_country_code,vehicle_.`second_jurisdiction_region_code` AS jurisdiction_region_code FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        findFirstRegistrationJurisdictionByIdQuery == 'SELECT vehicle_.`jurisdiction_country_code` AS country_code,vehicle_.`jurisdiction_region_code` AS region_code FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        findSecondRegistrationJurisdictionByIdQuery == 'SELECT vehicle_.`second_jurisdiction_country_code` AS country_code,vehicle_.`second_jurisdiction_region_code` AS region_code FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
    }

    void "test embedded projection result preserves leaf aliases and read transformers"() {
        given:
        def repository = buildRepository('test.LocationRestaurantRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@Embeddable
class LocationLabel {
    @MappedProperty(alias = "ll")
    private String label;

    @DataTransformer(read = "LOWER(@.pref_normalized_code)")
    @MappedProperty("normalized_code")
    private String normalizedCode;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNormalizedCode() {
        return normalizedCode;
    }

    public void setNormalizedCode(String normalizedCode) {
        this.normalizedCode = normalizedCode;
    }
}

@MappedEntity
class LocationRestaurant {
    @GeneratedValue
    @Id
    private Long id;

    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("pref_")
    private LocationLabel location;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocationLabel getLocation() {
        return location;
    }

    public void setLocation(LocationLabel location) {
        this.location = location;
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface LocationRestaurantRepository extends GenericRepository<LocationRestaurant, Long> {
    LocationLabel findLocationById(Long id);
}
""")

        def findLocationByIdMethod = repository.getRequiredMethod("findLocationById", Long)

        expect:
        getQuery(findLocationByIdMethod) == 'SELECT location_restaurant_.`pref_label` AS ll,LOWER(location_restaurant_.pref_normalized_code) AS normalized_code FROM `location_restaurant` location_restaurant_ WHERE (location_restaurant_.`id` = ?)'
        getResultDataType(findLocationByIdMethod) == DataType.ENTITY
    }

    void "test invalid embedded projection result"() {
        when:
        buildRepository('test.RestaurantRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Restaurant;
import io.micronaut.data.tck.entities.ShipmentId;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.MYSQL)
interface RestaurantRepository extends GenericRepository<Restaurant, Long> {
    Optional<ShipmentId> findAddressByName(String name);
}
""")
        then:
        Throwable ex = thrown()
        ex.message.contains("method returns an incompatible type")
    }

    void "test nested embedded projection result"() {
        given:
        def repository = buildRepository('test.VehicleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Jurisdiction;
import io.micronaut.data.tck.entities.Registration;
import io.micronaut.data.tck.entities.Vehicle;

@JdbcRepository(dialect = Dialect.H2)
interface VehicleRepository extends GenericRepository<Vehicle, Long> {

    Registration findFirstRegistrationById(Long id);

    Registration findSecondRegistrationById(Long id);

    Jurisdiction findFirstRegistrationJurisdictionById(Long id);

    Jurisdiction findSecondRegistrationJurisdictionById(Long id);
}

""")

        def firstRegistrationMethod = repository.getRequiredMethod("findFirstRegistrationById", Long)
        def secondRegistrationMethod = repository.getRequiredMethod("findSecondRegistrationById", Long)
        def firstJurisdictionMethod = repository.getRequiredMethod("findFirstRegistrationJurisdictionById", Long)
        def secondJurisdictionMethod = repository.getRequiredMethod("findSecondRegistrationJurisdictionById", Long)

        expect:
        getQuery(firstRegistrationMethod) == 'SELECT vehicle_.`plate_number`,vehicle_.`status`,vehicle_.`jurisdiction_country_code`,vehicle_.`jurisdiction_region_code` FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        getQuery(secondRegistrationMethod) == 'SELECT vehicle_.`second_plate_number` AS plate_number,vehicle_.`second_status` AS status,vehicle_.`second_jurisdiction_country_code` AS jurisdiction_country_code,vehicle_.`second_jurisdiction_region_code` AS jurisdiction_region_code FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        getQuery(firstJurisdictionMethod) == 'SELECT vehicle_.`jurisdiction_country_code` AS country_code,vehicle_.`jurisdiction_region_code` AS region_code FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        getQuery(secondJurisdictionMethod) == 'SELECT vehicle_.`second_jurisdiction_country_code` AS country_code,vehicle_.`second_jurisdiction_region_code` AS region_code FROM `vehicle` vehicle_ WHERE (vehicle_.`id` = ?)'
        getResultDataType(firstRegistrationMethod) == DataType.ENTITY
        getResultDataType(secondRegistrationMethod) == DataType.ENTITY
        getResultDataType(firstJurisdictionMethod) == DataType.ENTITY
        getResultDataType(secondJurisdictionMethod) == DataType.ENTITY
    }

    void "test count query with joins"() {
        given:
        def repository = buildRepository('test.AuthorRepository', """
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect = Dialect.H2)
interface AuthorRepository extends GenericRepository<Author, Long> {

    @Where("@.nick_name = :nickName")
    @Join(value = "books", type = Join.Type.FETCH)
    Page<Author> findByName(String name, String nickName, Pageable pageable);

    @Where("@.name = :name")
    Page<Author> findByNickName(String nickName, String name, Pageable pageable);

    @Join(value = "books", type = Join.Type.FETCH)
    Page<Author> findAll(Pageable pageable);
}

""")

        def findAllMethod = repository.getRequiredMethod("findAll", Pageable)
        def findAllQuery = getQuery(findAllMethod)
        def findAllCountQuery = getCountQuery(findAllMethod)
        def findByNameMethod = repository.getRequiredMethod("findByName", String, String, Pageable)
        def findByNameQuery = getQuery(findByNameMethod)
        def findByNameCountQuery = getCountQuery(findByNameMethod)
        def findByNickNameMethod = repository.getRequiredMethod("findByNickName", String, String, Pageable)
        def findByNickNameQuery = getQuery(findByNickNameMethod)
        def findByNickNameCountQuery = getCountQuery(findByNickNameMethod)

        expect:
        findAllQuery == 'SELECT author_.`id`,author_.`name`,author_.`nick_name`,author_books_.`id` AS books_id,author_books_.`author_id` AS books_author_id,author_books_.`genre_id` AS books_genre_id,author_books_.`title` AS books_title,author_books_.`total_pages` AS books_total_pages,author_books_.`publisher_id` AS books_publisher_id,author_books_.`last_updated` AS books_last_updated FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id` WHERE (author_.`id` IN (SELECT author_author_.`id` FROM `author` author_author_ WHERE (author_author_.`id` IN (SELECT author_author_author_.`id` FROM `author` author_author_author_ INNER JOIN `book` author_author_author_books_ ON author_author_author_.`id`=author_author_author_books_.`author_id`))'
        findAllCountQuery == 'SELECT COUNT(DISTINCT(author_.`id`)) FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id`'
        findByNameQuery == 'SELECT author_.`id`,author_.`name`,author_.`nick_name`,author_books_.`id` AS books_id,author_books_.`author_id` AS books_author_id,author_books_.`genre_id` AS books_genre_id,author_books_.`title` AS books_title,author_books_.`total_pages` AS books_total_pages,author_books_.`publisher_id` AS books_publisher_id,author_books_.`last_updated` AS books_last_updated FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id` WHERE (author_.`id` IN (SELECT author_author_.`id` FROM `author` author_author_ WHERE (author_author_.`id` IN (SELECT author_author_author_.`id` FROM `author` author_author_author_ INNER JOIN `book` author_author_author_books_ ON author_author_author_.`id`=author_author_author_books_.`author_id` WHERE (author_author_author_.`name` = ?)))'
        findByNameCountQuery == 'SELECT COUNT(DISTINCT(author_.`id`)) FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id` WHERE (author_.`name` = ? AND author_.nick_name = ?)'
        findByNickNameQuery == 'SELECT author_.`id`,author_.`name`,author_.`nick_name` FROM `author` author_ WHERE (author_.`nick_name` = ? AND author_.name = ?)'
        findByNickNameCountQuery == 'SELECT COUNT(*) FROM `author` author_ WHERE (author_.`nick_name` = ? AND author_.name = ?)'
        getResultDataType(findAllMethod) == DataType.ENTITY
        getResultDataType(findByNameMethod) == DataType.ENTITY
        getResultDataType(findByNickNameMethod) == DataType.ENTITY
    }

    void "test count query with joins - expressions"() {
        given:
            def repository = buildRepository('test.AuthorRepository', """
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect = Dialect.H2)
interface AuthorRepository extends GenericRepository<Author, Long> {

    // With books join, making sure count query doesn't use join
    @Where("@.nick_name = :nickName")
    @Join(value = "books", type = Join.Type.FETCH)
    @ParameterExpression(name = "nickName", expression = "#{this.myNickName()}")
    Page<Author> findByName(String name, Pageable pageable);

    @Where("@.name = :name")
    @ParameterExpression(name = "name", expression = "#{this.myName()}")
    Page<Author> findByNickName(String nickName, Pageable pageable);

    @Join(value = "books", type = Join.Type.FETCH)
    Page<Author> findAll(Pageable pageable);

    default String myNickName() {
        return "xyz123";
    }

    default String myName() {
        return "abc123";
    }

}

""")

            def findAllMethod = repository.getRequiredMethod("findAll", Pageable)
            def findAllQuery = getQuery(findAllMethod)
            def findAllCountQuery = getCountQuery(findAllMethod)
            def findByNameMethod = repository.getRequiredMethod("findByName", String, Pageable)
            def findByNameQuery = getQuery(findByNameMethod)
            def findByNameExpressions = getParameterExpressions(findByNameMethod);
            def findByNameCountQuery = getCountQuery(findByNameMethod)
            def findByNickNameMethod = repository.getRequiredMethod("findByNickName", String, Pageable)
            def findByNickNameQuery = getQuery(findByNickNameMethod)
            def findByNickNameCountQuery = getCountQuery(findByNickNameMethod)
            def findByNickNameExpressions = getParameterExpressions(findByNickNameMethod);

        expect:
            findAllQuery == 'SELECT author_.`id`,author_.`name`,author_.`nick_name`,author_books_.`id` AS books_id,author_books_.`author_id` AS books_author_id,author_books_.`genre_id` AS books_genre_id,author_books_.`title` AS books_title,author_books_.`total_pages` AS books_total_pages,author_books_.`publisher_id` AS books_publisher_id,author_books_.`last_updated` AS books_last_updated FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id` WHERE (author_.`id` IN (SELECT author_author_.`id` FROM `author` author_author_ WHERE (author_author_.`id` IN (SELECT author_author_author_.`id` FROM `author` author_author_author_ INNER JOIN `book` author_author_author_books_ ON author_author_author_.`id`=author_author_author_books_.`author_id`))'
            findAllCountQuery == 'SELECT COUNT(DISTINCT(author_.`id`)) FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id`'
            findByNameQuery == 'SELECT author_.`id`,author_.`name`,author_.`nick_name`,author_books_.`id` AS books_id,author_books_.`author_id` AS books_author_id,author_books_.`genre_id` AS books_genre_id,author_books_.`title` AS books_title,author_books_.`total_pages` AS books_total_pages,author_books_.`publisher_id` AS books_publisher_id,author_books_.`last_updated` AS books_last_updated FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id` WHERE (author_.`id` IN (SELECT author_author_.`id` FROM `author` author_author_ WHERE (author_author_.`id` IN (SELECT author_author_author_.`id` FROM `author` author_author_author_ INNER JOIN `book` author_author_author_books_ ON author_author_author_.`id`=author_author_author_books_.`author_id` WHERE (author_author_author_.`name` = ?)))'
            findByNameCountQuery == 'SELECT COUNT(DISTINCT(author_.`id`)) FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id` WHERE (author_.`name` = ? AND author_.nick_name = ?)'
            findByNickNameQuery == 'SELECT author_.`id`,author_.`name`,author_.`nick_name` FROM `author` author_ WHERE (author_.`nick_name` = ? AND author_.name = ?)'
            findByNickNameCountQuery == 'SELECT COUNT(*) FROM `author` author_ WHERE (author_.`nick_name` = ? AND author_.name = ?)'
            getResultDataType(findAllMethod) == DataType.ENTITY
            getResultDataType(findByNameMethod) == DataType.ENTITY
            getResultDataType(findByNickNameMethod) == DataType.ENTITY
            findByNameExpressions == [false, false, true, false] as Boolean[]
            findByNickNameExpressions == [false, true, false] as Boolean[]
    }

    void "test distinct query"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Person;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.MYSQL)
interface PersonRepository extends GenericRepository<Person, Long> {

    List<Person> findDistinct();

    List<Person> findDistinctIdAndName();
}
""")
        def distinctQuery = getQuery(repository.getRequiredMethod("findDistinct"))
        def distinctIdAndNameQuery = getQuery(repository.getRequiredMethod("findDistinctIdAndName"))

        expect:
        distinctQuery == 'SELECT DISTINCT person_.`id`,person_.`name`,person_.`age`,person_.`enabled`,person_.`income` FROM `person` person_'
        distinctIdAndNameQuery == 'SELECT DISTINCT person_.`id`,person_.`name` FROM `person` person_'
    }

    void "test findBy join query with join column"() {
        given:
        def repository = buildRepository('test.EmployeeGroupRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.jdbc.entities.EmployeeGroup;

@JdbcRepository(dialect = Dialect.H2)
interface EmployeeGroupRepository extends GenericRepository<EmployeeGroup, Long> {

    EmployeeGroup save(EmployeeGroup employeeGroup);

    @Join(value = "employees", alias = "employee_", type = Join.Type.LEFT_FETCH)
    @Where("@.category_id = :categoryId")
    List<EmployeeGroup> findByCategoryId(Long categoryId);
}
""")
        def saveQuery = getQuery(repository.getRequiredMethod("save", EmployeeGroup))
        def findByCategoryIdQuery = getQuery(repository.getRequiredMethod("findByCategoryId", Long))

        expect:"Join does not use join table because of JoinColumn annotation"
        saveQuery == 'INSERT INTO `employee_group` (`name`,`category_id`,`employer_id`) VALUES (?,?,?)'
        findByCategoryIdQuery == 'SELECT employee_group_.`id`,employee_group_.`name`,employee_group_.`category_id`,employee_group_.`employer_id`,employee_.`id` AS employee_id,employee_.`name` AS employee_name,employee_.`category_id` AS employee_category_id,employee_.`employer_id` AS employee_employer_id FROM `employee_group` employee_group_ LEFT JOIN `employee` employee_ ON employee_group_.`employer_id`=employee_.`employer_id` AND employee_group_.`category_id`=employee_.`category_id` WHERE (employee_group_.`category_id` = ? AND employee_group_.category_id = ?)'
    }

    void "test many-to-one with join column"() {
        given:
        def repository = buildRepository('test.CustomBookRepository', """
import io.micronaut.data.annotation.*;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import jakarta.persistence.JoinColumn;
@JdbcRepository(dialect = Dialect.H2)
@Join("author")
interface CustomBookRepository extends GenericRepository<CustomBook, Long> {
    List<CustomBook> findAll();

    CustomBook save(CustomBook book);
}
@MappedEntity
class CustomAuthor {
    @GeneratedValue
    @Id
    private Long id;
    private Long id2;
    private String name;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getId2() { return id2; }
    public void setId2(Long id2) { this.id2 = id2; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
@MappedEntity
class CustomBook {
    @GeneratedValue
    @Id
    private Long id;
    private String title;
    private int pages;
    @Relation(Relation.Kind.MANY_TO_ONE)
    @JoinColumn(name = "author_id2", referencedColumnName = "id2")
    private CustomAuthor author;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }
    public CustomAuthor getAuthor() { return author; }
    public void setAuthor(CustomAuthor author) { this.author = author; }
}
""")

        def findAllMethod = repository.getRequiredMethod("findAll")
        def findAllQuery = getQuery(findAllMethod)

        expect:
        findAllQuery == 'SELECT custom_book_.`id`,custom_book_.`title`,custom_book_.`pages`,custom_book_.`author_id2`,custom_book_author_.`id2` AS author_id2,custom_book_author_.`name` AS author_name FROM `custom_book` custom_book_ INNER JOIN `custom_author` custom_book_author_ ON custom_book_.`author_id2`=custom_book_author_.`id2`'
        getResultDataType(findAllMethod) == DataType.ENTITY
    }

    void "test DTO with association and join"() {
        given:
            def repository = buildRepository('test.AuthorRepository', """
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.AuthorDtoWithBooks;

@JdbcRepository(dialect = Dialect.H2)
interface AuthorRepository extends GenericRepository<Author, Long> {

    @Join("books")
    List<AuthorDtoWithBooks> queryAll();
}

""")

            def queryAllMethod = repository.getRequiredMethod("queryAll")
            def queryAllQuery = getQuery(queryAllMethod)

        expect:
            queryAllQuery == 'SELECT author_.`id`,author_books_.`id` AS books_id,author_books_.`author_id` AS books_author_id,author_books_.`genre_id` AS books_genre_id,author_books_.`title` AS books_title,author_books_.`total_pages` AS books_total_pages,author_books_.`publisher_id` AS books_publisher_id,author_books_.`last_updated` AS books_last_updated FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id`'
            getResultDataType(queryAllMethod) == DataType.OBJECT
    }

    void "test embedded id with many-to-one counts"() {
        given:
        def repository = buildRepository('test.UserRoleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.jdbc.entities.UserRole;
import io.micronaut.data.tck.jdbc.entities.UserRoleId;

@JdbcRepository(dialect = Dialect.H2)
interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {

    long count();

    long countDistinct();
}

""")
        def countQuery = getQuery(repository.getRequiredMethod("count"))
        def countDistinctQuery = getQuery(repository.getRequiredMethod("countDistinct"))
        expect:
        countQuery == 'SELECT COUNT(*) FROM `user_role_composite` user_role_'
        countDistinctQuery == 'SELECT COUNT(DISTINCT( CONCAT(user_role_.`user_id`,user_role_.`role_id`))) FROM `user_role_composite` user_role_'
    }

    void "test many-to-one with properties starting with the same prefix"() {
        given:
        def repository = buildRepository('test.UserGroupMembershipRepository', """

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@MappedEntity(value = "ua", alias = "ua_")
class Address {
    @Id
    private Long id;
    private String zipCode;
    private String city;
    private String street;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
}
@MappedEntity(value = "u", alias = "u_")
class User {
    @Id
    private Long id;
    private String login;
    private Address address;
    private String addressZipCode;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public String getAddressZipCode() { return addressZipCode; }
    public void setAddressZipCode(String addressZipCode) { this.addressZipCode = addressZipCode; }
}
@MappedEntity(value = "a", alias = "a_")
class Area {
    @Id
    private Long id;
    private String name;
    public Long getId() { return id;}
    public void setId(Long id) { this.id = id; }
    public String getName() { return name;}
    public void setName(String name) { this.name = name; }
}
@MappedEntity(value = "ug", alias = "ug_")
class UserGroup {
    @Id
    private Long id;
    @OneToMany(mappedBy = "userGroup", fetch = FetchType.LAZY)
    private Set<UserGroupMembership> userAuthorizations = new HashSet<>();
    @ManyToOne
    private Area area;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Set<UserGroupMembership> getUserAuthorizations() { return userAuthorizations; }
    public void setUserAuthorizations(Set<UserGroupMembership> userAuthorizations) { this.userAuthorizations = userAuthorizations; }
    public Area getArea() { return area; }
    public void setArea(Area area) { this.area = area; }
}
@MappedEntity(value = "ugm", alias = "ugm_")
class UserGroupMembership {
    @Id
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER)
    private UserGroup userGroup;
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;
    @ManyToOne
    private Address userAddress;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UserGroup getUserGroup() { return userGroup; }
    public void setUserGroup(UserGroup userGroup) { this.userGroup = userGroup; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Address getUserAddress() { return userAddress; }
    public void setUserAddress(Address userAddress) { this.userAddress = userAddress; }
}
@JdbcRepository(dialect = Dialect.MYSQL)
interface UserGroupMembershipRepository extends GenericRepository<UserGroupMembership, Long> {

    @Join(value = "userGroup.area", type = Join.Type.FETCH)
    List<UserGroupMembership> findAllByUserLoginAndUserGroup_AreaId(String login, Long uid);

    List<UserGroupMembership> findAllByUserLogin(String userLogin);

    List<UserGroupMembership> findAllByUser_AddressZipCode(String zipCode);

    List<UserGroupMembership> findAllByUserAddress_ZipCode(String zipCode);
}
"""
        )

        expect:"The repository to compile"
        repository != null
        when:
        def queryByUserLoginAndAreaId = getQuery(repository.getRequiredMethod("findAllByUserLoginAndUserGroup_AreaId", String, Long))
        def queryByUserLogin = getQuery(repository.getRequiredMethod("findAllByUserLogin", String))
        def queryByUserAddressZipCode = getQuery(repository.getRequiredMethod("findAllByUser_AddressZipCode", String))
        def queryByUserAddressZipCode2 = getQuery(repository.getRequiredMethod("findAllByUserAddress_ZipCode", String))
        then:
        queryByUserLoginAndAreaId != ''
        queryByUserLogin == 'SELECT ugm_.`id`,ugm_.`user_group_id`,ugm_.`user_id`,ugm_.`user_address_id` FROM `ugm` ugm_ INNER JOIN `u` ugm_user_ ON ugm_.`user_id`=ugm_user_.`id` WHERE (ugm_user_.`login` = ?)'
        // Queries by user.addressZipCode
        queryByUserAddressZipCode == 'SELECT ugm_.`id`,ugm_.`user_group_id`,ugm_.`user_id`,ugm_.`user_address_id` FROM `ugm` ugm_ INNER JOIN `u` ugm_user_ ON ugm_.`user_id`=ugm_user_.`id` WHERE (ugm_user_.`address_zip_code` = ?)'
        // Queries by userAddress.zipCode
        queryByUserAddressZipCode2 == 'SELECT ugm_.`id`,ugm_.`user_group_id`,ugm_.`user_id`,ugm_.`user_address_id` FROM `ugm` ugm_ INNER JOIN `ua` ugm_user_address_ ON ugm_.`user_address_id`=ugm_user_address_.`id` WHERE (ugm_user_address_.`zip_code` = ?)'
    }

    void "test repo method with underscore and not matching property"() {
        when:
        def repository = buildRepository('test.BookRepository', """

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.MYSQL)
interface BookRepository extends GenericRepository<Book, Long> {

    @Join(value = "author", type = Join.Type.FETCH)
    List<Book> findAllByPublisherZipCodeAndAuthor_SpecName(String zipCode, String specName);
}
"""
        )

        then:
        Throwable ex = thrown()
        ex.message.contains('Invalid path [SpecName] of [io.micronaut.data.tck.entities.Author]')
    }

    void "test entity with different id mapping"() {
        when:
            def repository = buildRepository('test.H2NoIdEntityRepository', '''
import io.micronaut.data.annotation.MappedProperty;
import java.sql.Time;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.entities.NoIdEntity;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.H2)
interface H2NoIdEntityRepository extends CrudRepository<NoIdEntity, Long> {
}

''')
        then:
            noExceptionThrown()
    }

    void "test embedded id join"() {
        given:
        def repository = buildRepository('test.TestRepository', '''

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.processor.entity.ActivityPeriodEntity;

@JdbcRepository(dialect = Dialect.H2)
interface TestRepository extends GenericRepository<ActivityPeriodEntity, UUID> {

    @Join(value = "persons.id.person", type = Join.Type.LEFT)
    List<ActivityPeriodEntity> findAll();
}

''')
        expect:"The repository to compile"
        repository != null
        when:
        def queryFindAll = getQuery(repository.getRequiredMethod("findAll"))
        then:
        queryFindAll == 'SELECT activity_period_entity_.`id`,activity_period_entity_.`name`,activity_period_entity_.`description`,activity_period_entity_.`type` FROM `activity_period` activity_period_entity_ LEFT JOIN `activity_period_person` activity_period_entity_persons_ ON activity_period_entity_.`id`=activity_period_entity_persons_.`activity_period_id` LEFT JOIN `activity_person` activity_period_entity_persons_id_person_ ON activity_period_entity_persons_.`person_id`=activity_period_entity_persons_id_person_.`id`'
        when:
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()
        def query = builder.createQuery()
        def root = query.from(ActivityPeriodEntity)
        root.join("persons.id.person", Join.Type.LEFT)
        def result = query.build(new SqlQueryBuilder(Dialect.H2))
        then:
        result.query == queryFindAll
    }

    void "test project enum"() {
        when:
            def repository = buildRepository('test.PetRepository', """
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Pet;
import reactor.core.publisher.Flux;

@Repository
interface PetRepository extends GenericRepository<Pet, UUID> {

    Flux<Pet.PetType> listDistinctType();

}
"""
            )


            def method = repository.getRequiredMethod("listDistinctType")
        then:
            getQuery(method) == 'SELECT DISTINCT pet_.type FROM io.micronaut.data.tck.entities.Pet AS pet_'
            getResultDataType(method) == DataType.STRING
    }

    void "test project max"() {
        when:
            def repository = buildRepository('test.StudentRepository', """
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Pet;
import io.micronaut.data.tck.entities.Student;

@Repository
interface StudentRepository extends GenericRepository<Student, Long> {

    int findMaxIdByIdIn(List<Long> ids);

}
"""
            )


            def method = repository.getRequiredMethod("findMaxIdByIdIn", List)
        then:
            getQuery(method) == 'SELECT MAX(student_.id) FROM io.micronaut.data.tck.entities.Student AS student_ WHERE (student_.id IN (:p1))'
            getResultDataType(method) == DataType.LONG
    }

    void "test count query for entity with composite id"() {
        given:
        def repository = buildRepository('test.UserRoleRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.jdbc.entities.UserRole;
import io.micronaut.data.tck.jdbc.entities.UserRoleId;

@JdbcRepository(dialect = Dialect.MYSQL)
interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {

    int count();

    int countDistinct();
}
""")
        def countQuery = getQuery(repository.getRequiredMethod("count"))
        def countDistinctQuery = getQuery(repository.getRequiredMethod("countDistinct"))

        expect:
        countQuery == 'SELECT COUNT(*) FROM `user_role_composite` user_role_'
        countDistinctQuery == 'SELECT COUNT(DISTINCT( CONCAT(user_role_.`user_id`,user_role_.`role_id`))) FROM `user_role_composite` user_role_'
    }

    void "test escape query"() {
        given:
            def repository = buildRepository('test.UserRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.User;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface UserRepository extends GenericRepository<User, Long> {

@Query("update \\"user\\" set locked=true where id=:id")
void lock(Long id);

}
""")
            def lockMethod = repository.getRequiredMethod("lock", Long)
        expect:
            getQuery(lockMethod) == 'update "user" set locked=true where id=:id'
            getRawQuery(lockMethod) == 'update "user" set locked=true where id=?'
    }

    void "test query with a tenant id"() {
        given:
        def repository = buildRepository('test.AccountRepository', """

import io.micronaut.data.annotation.WithTenantId;
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Account;

@JdbcRepository(dialect = Dialect.MYSQL)
interface AccountRepository extends GenericRepository<Account, Long> {

    @WithoutTenantId
    List<Account> findAll\$withAllTenants();

    @WithTenantId("bar")
    List<Account> findAll\$withTenantBar();

    @WithTenantId("foo")
    List<Account> findAll\$withTenantFoo();

    List<Account> findAll();

    Account findOneByName(String name);
}
""")
            def findOneByNameMethod = repository.getRequiredMethod("findOneByName", String)
            def findAll__withAllTenantsMethod = repository.findPossibleMethods("findAll\$withAllTenants").findFirst().get()
            def findAll__withTenantBar = repository.findPossibleMethods("findAll\$withTenantBar").findFirst().get()
            def findAll__withTenantFoo = repository.findPossibleMethods("findAll\$withTenantFoo").findFirst().get()
        expect:
            getQuery(repository.getRequiredMethod("findAll")) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`tenancy` = ?)'
            getQuery(findOneByNameMethod) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`name` = ? AND account_.`tenancy` = ?)'
            getParameterPropertyPaths(findOneByNameMethod) == ["name", "tenancy"] as String[]
            getQuery(findAll__withAllTenantsMethod) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_'
            getQuery(findAll__withTenantBar) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`tenancy` = \'bar\')'
            getQuery(findAll__withTenantFoo) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`tenancy` = \'foo\')'
    }

    void "test composite id"() {
        given:
        def repository = buildRepository('test.EntityWithIdClassRepository', """

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
interface EntityWithIdClassRepository extends CrudRepository<EntityWithIdClass, EntityIdClass> {
    List<EntityWithIdClass> findById1(Long id1);
    List<EntityWithIdClass> findById2(Long id2);
    long countDistinct();
    long countDistinctName();
}

""")
            def findById1 = repository.findPossibleMethods("findById1").findFirst().get()
            def findById2 = repository.findPossibleMethods("findById2").findFirst().get()
        expect:
            getQuery(findById1) == 'SELECT entity_with_id_class_.`id1`,entity_with_id_class_.`id2`,entity_with_id_class_.`name` FROM `entity_with_id_class` entity_with_id_class_ WHERE (entity_with_id_class_.`id1` = ?)'
            getQuery(findById2) == 'SELECT entity_with_id_class_.`id1`,entity_with_id_class_.`id2`,entity_with_id_class_.`name` FROM `entity_with_id_class` entity_with_id_class_ WHERE (entity_with_id_class_.`id2` = ?)'
    }

    void "test count with join"() {
        given:
        def repository = buildRepository('test.EntityWithIdClassRepository', """

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;

import java.util.List;

@Repository
interface EntityWithIdClassRepository extends GenericRepository<Book, Long> {
   @Join("author")
   Page<Book> findAll(Pageable pageable);
}

""")
            def findAll = repository.findPossibleMethods("findAll").findFirst().get()
        expect:
            getQuery(findAll) == 'SELECT book_ FROM io.micronaut.data.tck.entities.Book AS book_ JOIN FETCH book_.author book_author_ WHERE (book_.id IN (SELECT book_book_.id FROM io.micronaut.data.tck.entities.Book AS book_book_ WHERE (book_book_.id IN (SELECT book_book_book_.id FROM io.micronaut.data.tck.entities.Book AS book_book_book_ JOIN book_book_book_.author book_book_book_author_))))'
            getCountQuery(findAll) == 'SELECT COUNT(DISTINCT(book_)) FROM io.micronaut.data.tck.entities.Book AS book_ JOIN book_.author book_author_'
    }

    void "test criteria"() {
        given:
        def repository = buildRepository('test.BookRepository', """

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.tck.entities.Book;

import java.util.List;

@Repository
interface BookRepository extends GenericRepository<Book, Long> {

   List<Book> findAllBooksByCriteria1(CriteriaQueryBuilder<Book> builder);
   List<Book> findAllBooksByCriteria2(QuerySpecification<Book> spec);
   List<Book> findAllBooksByCriteria3(PredicateSpecification<Book> spec);

   Book findBookByCriteria1(CriteriaQueryBuilder<Book> builder);
   Book findBookByCriteria2(QuerySpecification<Book> spec);
   Book findBookByCriteria3(PredicateSpecification<Book> spec);

   Long findCountByCriteria1(CriteriaQueryBuilder<Long> builder);
   Long findCountByCriteria2(QuerySpecification<Long> spec);
   Long findCountByCriteria3(PredicateSpecification<Long> spec);

   Long findOne(CriteriaQueryBuilder<Long> builder);
   List<String> findAll(CriteriaQueryBuilder<String> builder);
}

""")
        when:
            def findAllBooksByCriteria1 = repository.findPossibleMethods("findAllBooksByCriteria1").findFirst().get()
            def findAllBooksByCriteria2 = repository.findPossibleMethods("findAllBooksByCriteria2").findFirst().get()
            def findAllBooksByCriteria3 = repository.findPossibleMethods("findAllBooksByCriteria3").findFirst().get()
        then:
            getResultDataType(findAllBooksByCriteria1) == DataType.ENTITY
            getResultDataType(findAllBooksByCriteria2) == DataType.ENTITY
            getResultDataType(findAllBooksByCriteria3) == DataType.ENTITY
            getDataInterceptor(findAllBooksByCriteria1) == "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor"
            getDataInterceptor(findAllBooksByCriteria2) == "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor"
            getDataInterceptor(findAllBooksByCriteria3) == "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor"

        when:
            def findBookByCriteria1 = repository.findPossibleMethods("findBookByCriteria1").findFirst().get()
            def findBookByCriteria2 = repository.findPossibleMethods("findBookByCriteria2").findFirst().get()
            def findBookByCriteria3 = repository.findPossibleMethods("findBookByCriteria3").findFirst().get()
        then:
            getResultDataType(findBookByCriteria1) == DataType.ENTITY
            getResultDataType(findBookByCriteria2) == DataType.ENTITY
            getResultDataType(findBookByCriteria3) == DataType.ENTITY
            getDataInterceptor(findBookByCriteria1) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"
            getDataInterceptor(findBookByCriteria2) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"
            getDataInterceptor(findBookByCriteria3) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"

        when:
            def findCountByCriteria1 = repository.findPossibleMethods("findCountByCriteria1").findFirst().get()
            def findCountByCriteria2 = repository.findPossibleMethods("findCountByCriteria2").findFirst().get()
            def findCountByCriteria3 = repository.findPossibleMethods("findCountByCriteria3").findFirst().get()
        then:
            getResultDataType(findCountByCriteria1) == DataType.LONG
            getResultDataType(findCountByCriteria2) == DataType.LONG
            getResultDataType(findCountByCriteria3) == DataType.LONG
            getDataInterceptor(findCountByCriteria1) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"
            getDataInterceptor(findCountByCriteria2) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"
            getDataInterceptor(findCountByCriteria3) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"

        when:
            def findOne = repository.findPossibleMethods("findOne").findFirst().get()
        then:
            getResultDataType(findOne) == DataType.LONG
            getDataInterceptor(findOne) == "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor"

        when:
            def findAll = repository.findPossibleMethods("findAll").findFirst().get()
        then:
            getResultDataType(findAll) == DataType.STRING
            getDataInterceptor(findAll) == "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor"
    }

    void "test embedded id"() {
        given:
        def repository = buildRepository('test.SettlementRepository', """

import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.H2)
interface SettlementRepository extends CrudRepository<Settlement, SettlementPk> {
    @Join(value = "settlementType")
    @Join(value = "zone")
    @Override
    Optional<Settlement> findById(@NonNull SettlementPk settlementPk);

    @Join(value = "settlementType")
    @Join(value = "zone")
    @Join(value = "id.county")
    Optional<Settlement> queryById(@NonNull SettlementPk settlementPk);

    @Join(value = "settlementType")
    @Join(value = "zone")
    @Join(value = "id.county")
    List<Settlement> findAll(Pageable pageable);
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity("comp_state")
class State {
    @Id
    Integer id;
    @MappedProperty
    String stateName;
    @MappedProperty("is_enabled")
    Boolean enabled;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Embeddable
class CountyPk {
    @MappedProperty(value = "id")
    Integer id;
    @MappedProperty(value = "state_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    State state;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity("comp_country")
class County {
    @EmbeddedId
    @MappedProperty(value = "id")
    CountyPk id;
    @MappedProperty
    String countyName;
    @MappedProperty("is_enabled")
    Boolean enabled;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Embeddable
class SettlementPk {
    @MappedProperty(value = "code")
    String code;

    @MappedProperty(value = "code_id")
    Integer codeId;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    County county;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity("comp_settlement")
class Settlement {
    @EmbeddedId
    @MappedProperty(value = "id")
    SettlementPk id;
    @MappedProperty
    String description;
    @Relation(Relation.Kind.MANY_TO_ONE)
    SettlementType settlementType;
    @Relation(Relation.Kind.MANY_TO_ONE)
    Zone zone;
    @MappedProperty("is_enabled")
    Boolean enabled;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity("comp_sett_type")
class SettlementType {
    @Id
    @GeneratedValue
    Long id;
    @MappedProperty
    String name;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity("comp_zone")
class Zone {
    @Id
    @GeneratedValue
    Long id;
    @MappedProperty
    String name;
}

""")
        when:
            def update = repository.findPossibleMethods("update").findFirst().get()
        then:
            getQuery(update) == "UPDATE `comp_settlement` SET `description`=?,`settlement_type_id`=?,`zone_id`=?,`is_enabled`=? WHERE (`code` = ? AND `code_id` = ? AND `id_county_id_id` = ? AND `id_county_id_state_id` = ?)"
    }

    void "test combined id"() {
        given:
        def repository = buildRepository('test.EntityWithIdClassRepository', """

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;import io.micronaut.data.tck.entities.EntityIdClass;
import io.micronaut.data.tck.entities.EntityWithIdClass;

import java.util.List;

@Repository
interface EntityWithIdClassRepository extends GenericRepository<EntityWithIdClass, EntityIdClass> {
    List<EntityWithIdClass> findById(EntityIdClass id);
    List<EntityWithIdClass> findById1(Long id1);
    List<EntityWithIdClass> findById2(Long id2);
}


""")
        when:
            def findById1 = repository.findPossibleMethods("findById1").findFirst().get()
            def findById = repository.findPossibleMethods("findById").findFirst().get()
        then:
            getQuery(findById1) == "SELECT entityWithIdClass_ FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_ WHERE (entityWithIdClass_.id1 = :p1)"
            getParameterBindingPaths(findById1) == [""] as String[]
            getParameterPropertyPaths(findById1) == ["id1"] as String[]
            getQuery(findById) == "SELECT entityWithIdClass_ FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_ WHERE (entityWithIdClass_.id1 = :p1 AND entityWithIdClass_.id2 = :p2)"
            getParameterBindingPaths(findById) == ["id1", "id2"] as String[]
            getParameterPropertyPaths(findById) == ["id1", "id2"] as String[]
    }

    void "test projection"() {
        given:
        def repository = buildRepository('test.BookRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

import java.util.List;

@JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.POSTGRES)
interface BookRepository extends GenericRepository<Book, Long> {
    List<Book> queryTop3ByAuthorNameOrderByTitle(String name);
}

""")
        when:
            def queryTop3ByAuthorNameOrderByTitle = repository.findPossibleMethods("queryTop3ByAuthorNameOrderByTitle").findFirst().get()
        then:
            getQuery(queryTop3ByAuthorNameOrderByTitle) == '''SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_ INNER JOIN "author" book_author_ ON book_."author_id"=book_author_."id" WHERE (book_author_."name" = ?) ORDER BY book_."title" ASC LIMIT 3'''
            getParameterBindingPaths(queryTop3ByAuthorNameOrderByTitle) == [""] as String[]
            getParameterPropertyPaths(queryTop3ByAuthorNameOrderByTitle) == ["author.name"] as String[]
    }

    void "test projection find annotation"() {

        given:
        def repository = buildRepository('test.BookRepository', """

import io.micronaut.data.annotation.By;
import io.micronaut.data.annotation.Find;
import io.micronaut.data.annotation.First;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

import java.util.List;

import static io.micronaut.data.model.query.builder.sql.Dialect.*;

@JdbcRepository(dialect = POSTGRES)
interface BookRepository extends GenericRepository<Book, Long> {
    @Find
    @First(3)
    @OrderBy("title")
    List<Book> find(@By("author.name") String name);
}

""")
        when:
            def queryTop3ByAuthorNameOrderByTitle = repository.findPossibleMethods("find").findFirst().get()
        then:
            getQuery(queryTop3ByAuthorNameOrderByTitle) == '''SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_ INNER JOIN "author" book_author_ ON book_."author_id"=book_author_."id" WHERE (book_author_."name" = ?) ORDER BY book_."title" ASC LIMIT 3'''
            getParameterBindingPaths(queryTop3ByAuthorNameOrderByTitle) == [""] as String[]
            getParameterPropertyPaths(queryTop3ByAuthorNameOrderByTitle) == ["author.name"] as String[]
    }

    void "test association projection"() {
        given:
            def repository = buildRepository('test.BookRepository', '''
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.tck.repositories.AuthorRepository;

@JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.ANSI)
abstract class BookRepository extends io.micronaut.data.tck.repositories.BookRepository {

    public BookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

}
''')

        when:
            def method =  repository.findPossibleMethods("findByAuthorIsNull").findFirst().get()
        then:
            getQuery(method) == '''SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_ WHERE (book_."author_id" IS NULL)'''

    }

    void "test match by id"() {
        given:
            def repository = buildRepository('test.UserGroupMembershipRepository', """

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Account;

@JdbcRepository(dialect = Dialect.H2)
interface UserGroupMembershipRepository extends GenericRepository<UserGroupMembership, Long> {

    @Join(value = "userGroup.area", type = Join.Type.FETCH)
    List<UserGroupMembership> findAllByUserLoginAndUserGroup_AreaId(String login, Long uid);
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity(value = "ugm", alias = "ugm_")
class UserGroupMembership {

    @Id
    @GeneratedValue
    Long id;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    UserGroup userGroup;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    User user;

}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity(value = "ug", alias = "ug_")
class UserGroup {

    @Id
    @GeneratedValue
    Long id;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "userGroup")
    Set<UserGroupMembership> userAuthorizations = new HashSet<UserGroupMembership>();

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.PERSIST)
    Area area;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity(value = "a", alias = "a_")
class Area {

    @Id
    @GeneratedValue
    Long id;

    String name;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@MappedEntity(value = "u", alias = "u_")
class User {

    @Id
    @GeneratedValue
    Long id;

    String login;
}

""")
            def findAllByUserLoginAndUserGroup_AreaIdMethod = repository.findPossibleMethods("findAllByUserLoginAndUserGroup_AreaId").findFirst().get()
        expect:
            getQuery(findAllByUserLoginAndUserGroup_AreaIdMethod) == 'SELECT ugm_.`id`,ugm_.`user_group_id`,ugm_.`user_id`,ugm_user_group_area_.`name` AS user_group_area_name,ugm_user_group_.`area_id` AS user_group_area_id FROM `ugm` ugm_ INNER JOIN `u` ugm_user_ ON ugm_.`user_id`=ugm_user_.`id` INNER JOIN `ug` ugm_user_group_ ON ugm_.`user_group_id`=ugm_user_group_.`id` INNER JOIN `a` ugm_user_group_area_ ON ugm_user_group_.`area_id`=ugm_user_group_area_.`id` WHERE (ugm_user_.`login` = ? AND ugm_user_group_.`area_id` = ?)'
            getParameterPropertyPaths(findAllByUserLoginAndUserGroup_AreaIdMethod) == ["user.login", "userGroup.area.id"] as String[]
            getParameterBindingIndexes(findAllByUserLoginAndUserGroup_AreaIdMethod) == ["0", "1"] as String[]
            getParameterBindingPaths(findAllByUserLoginAndUserGroup_AreaIdMethod) == ["", ""] as String[]
    }

    void "test find Pageable with join criteria"() {
        given:
        def repository = buildRepository('test.WorkRequestRepository', """
import io.micronaut.data.annotation.*;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import jakarta.persistence.JoinColumn;
@JdbcRepository(dialect = Dialect.H2)
interface WorkRequestRepository extends GenericRepository<WorkRequest, Long> {
    @Join("resources")
    Page<WorkRequest> findByResourcesName(String name, Pageable pageable);
    @Join("resources")
    CursoredPage<WorkRequest> findByResourcesKind(String kind, CursoredPageable pageable);
}
@MappedEntity
class WorkRequest {
    @Id
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "workRequest")
    private List<Resource> resources = List.of();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Resource> getResources() { return resources; }
    public void setResources(List<Resource> resources) { this.resources = resources; }
}
@MappedEntity
class Resource {
    @Id
    private Long id;
    private String name;
    private int pages;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private WorkRequest workRequest;
    private String kind;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public WorkRequest getWorkRequest() { return workRequest; }
    public void setWorkRequest(WorkRequest workRequest) { this.workRequest = workRequest; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
}
""")

        def findByResourcesNameMethod = repository.getRequiredMethod("findByResourcesName", String, Pageable)
        def findByResourcesNameCountQuery = getCountQuery(findByResourcesNameMethod)
        def findByResourcesKindMethod = repository.getRequiredMethod("findByResourcesKind", String, CursoredPageable)
        def findByResourcesKindCountQuery = getCountQuery(findByResourcesKindMethod)

        expect:
        findByResourcesNameCountQuery == 'SELECT COUNT(DISTINCT(work_request_.`id`)) FROM `work_request` work_request_ INNER JOIN `resource` work_request_resources_ ON work_request_.`id`=work_request_resources_.`work_request_id` WHERE (work_request_resources_.`name` = ?)'
        findByResourcesKindCountQuery == 'SELECT COUNT(DISTINCT(work_request_.`id`)) FROM `work_request` work_request_ INNER JOIN `resource` work_request_resources_ ON work_request_.`id`=work_request_resources_.`work_request_id` WHERE (work_request_resources_.`kind` = ?)'
    }

    void "test find using LIKE with custom query builder"() {
        given:
        def repository = buildRepository('test.TestRepository', """

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.custom.CustomRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

import java.util.List;

@CustomRepository("book")
interface TestRepository extends GenericRepository<Book, Long> {
   List<Book> findByTitleLike(String title);
   Optional<Book> findByTitleContains(String title);
   @Nullable
   Book findByTitleIlike(String title);
   List<Book> findByTitleNotLike(String title);
}

""")
        def findByTitleContainsMethod = repository.getRequiredMethod("findByTitleContains", String)
        def findByTitleContainsQuery = getQuery(findByTitleContainsMethod)
        def findByTitleLikeMethod = repository.getRequiredMethod("findByTitleLike", String)
        def findByTitleLikeQuery = getQuery(findByTitleLikeMethod)
        def findByTitleIlikeMethod = repository.getRequiredMethod("findByTitleIlike", String)
        def findByTitleIlikeQuery = getQuery(findByTitleIlikeMethod)
        def findByTitleNotLikeMethod = repository.getRequiredMethod("findByTitleNotLike", String)
        def findByTitleNotLikeQuery = getQuery(findByTitleNotLikeMethod)
        expect:
        findByTitleContainsQuery.endsWith('FROM "book" book_ WHERE (book_."title" LIKE CONCAT(\'%\',?,\'%\'))')
        findByTitleLikeQuery.endsWith('FROM "book" book_ WHERE (book_."title" LIKE ?)')
        findByTitleIlikeQuery.endsWith('FROM "book" book_ WHERE (LOWER(book_."title") LIKE LOWER(?))')
        findByTitleNotLikeQuery.endsWith('FROM "book" book_ WHERE (book_."title" NOT LIKE ?)')
    }

    void "test IN"() {
        given:
        def repository = buildRepository('test.TestRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.MYSQL)
interface TestRepository extends GenericRepository<Book, Long> {
    List<Book> findByAuthorInList(List<Author> authors);
}

""")
        def findByAuthorInListMethod = repository.findPossibleMethods("findByAuthorInList").findFirst().get()
        expect:
            getQuery(findByAuthorInListMethod) == "SELECT book_.`id`,book_.`author_id`,book_.`genre_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_.`last_updated` FROM `book` book_ WHERE (book_.`author_id` IN (?))"
    }

    void "test JOIN pagination"() {
        given:
        def repository = buildRepository('test.TestRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TestRepository extends GenericRepository<Book, Long> {
    @Join(value = "students", type = Join.Type.LEFT)
    Page<Book> findAll(Pageable pageable);
}

""")
        def findByAuthorInListMethod = repository.findPossibleMethods("findAll").findFirst().get()
        def countQueryAnnotation = findByAuthorInListMethod.getAnnotation(DataMethod).getAnnotation(DataMethod.META_MEMBER_COUNT_QUERY).get()
        expect:
            getQuery(findByAuthorInListMethod) == """SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_ LEFT JOIN "book_student" book_students_book_student_ ON book_."id"=book_students_book_student_."book_id"  LEFT JOIN "student" book_students_ ON book_students_book_student_."student_id"=book_students_."id" WHERE (book_."id" IN (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" IN (SELECT book_book_book_."id" FROM "book" book_book_book_ LEFT JOIN "book_student" book_book_book_students_book_student_ ON book_book_book_."id"=book_book_book_students_book_student_."book_id"  LEFT JOIN "student" book_book_book_students_ ON book_book_book_students_book_student_."student_id"=book_book_book_students_."id"))"""
            countQueryAnnotation.stringValue().get() == """SELECT COUNT(DISTINCT(book_."id")) FROM "book" book_ LEFT JOIN "book_student" book_students_book_student_ ON book_."id"=book_students_book_student_."book_id"  LEFT JOIN "student" book_students_ ON book_students_book_student_."student_id"=book_students_."id\""""
    }

    void "test JOIN pagination 2"() {
        given:
        def repository = buildRepository('test.TestRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TestRepository extends GenericRepository<Book, Long> {
    @Join(value = "students", type = Join.Type.LEFT_FETCH)
    Page<Book> findAllByStudentsNameIn(List<String> names, Pageable pageable);
}

""")
        def findAllByStudentsNameIn = repository.findPossibleMethods("findAllByStudentsNameIn").findFirst().get()
        def countQueryAnnotation = findAllByStudentsNameIn.getAnnotation(DataMethod).getAnnotation(DataMethod.META_MEMBER_COUNT_QUERY).get()
        expect:
            getQuery(findAllByStudentsNameIn) == """SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated",book_students_."id" AS students_id,book_students_."version" AS students_version,book_students_."name" AS students_name,book_students_."creation_time" AS students_creation_time,book_students_."last_updated_time" AS students_last_updated_time FROM "book" book_ LEFT JOIN "book_student" book_students_book_student_ ON book_."id"=book_students_book_student_."book_id"  LEFT JOIN "student" book_students_ ON book_students_book_student_."student_id"=book_students_."id" WHERE (book_."id" IN (SELECT book_book_."id" FROM "book" book_book_ WHERE (book_book_."id" IN (SELECT book_book_book_."id" FROM "book" book_book_book_ LEFT JOIN "book_student" book_book_book_students_book_student_ ON book_book_book_."id"=book_book_book_students_book_student_."book_id"  LEFT JOIN "student" book_book_book_students_ ON book_book_book_students_book_student_."student_id"=book_book_book_students_."id" WHERE (book_book_book_students_."name" IN (?))))"""
            countQueryAnnotation.stringValue().get() == """SELECT COUNT(DISTINCT(book_."id")) FROM "book" book_ LEFT JOIN "book_student" book_students_book_student_ ON book_."id"=book_students_book_student_."book_id"  LEFT JOIN "student" book_students_ ON book_students_book_student_."student_id"=book_students_."id" WHERE (book_students_."name" IN (?))"""
            countQueryAnnotation.getAnnotations("parameters").size() == 1
    }

    void "test LIMIT method"() {
        given:
        def repository = buildRepository('test.TestRepository', """

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Limit;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface TestRepository extends GenericRepository<Book, Long> {
    List<Book> findAll(Limit limit);
}

""")
        def findAll = repository.findPossibleMethods("findAll").findFirst().get()
        expect:
            getQuery(findAll) == """SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_"""
            isExpandableQuery(findAll)
            getParameterRoles(findAll) == ["querylimit"]
    }

    void "test JOIN query method"() {
        given:
        def repository = buildRepository('test.TestRepository', """

import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
interface TestRepository extends GenericRepository<Book, Long> {
    @Join("author")
    @OrderBy("author.name")
    @OrderBy("title")
    Page<Book> findAll(Pageable pageable);
}

""")
        def findAll = repository.findPossibleMethods("findAll").findFirst().get()
        expect:
            getQuery(findAll) == """SELECT book_.`id`,book_.`author_id`,book_.`genre_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_.`last_updated`,book_author_.`name` AS author_name,book_author_.`nick_name` AS author_nick_name FROM `book` book_ INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` IN (SELECT book_book_.`id` FROM `book` book_book_ WHERE (book_book_.`id` IN (SELECT book_book_book_.`id` FROM `book` book_book_book_ INNER JOIN `author` book_book_book_author_ ON book_book_book_.`author_id`=book_book_book_author_.`id`))"""
            getParameterRoles(findAll) == ["pageableRequired", "sort"]
    }

    void "test repository with reused embedded entity"() {
        when:
        buildRepository('test.TestRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.embedded.BookEntity;
import io.micronaut.data.tck.entities.embedded.BookState;
import io.micronaut.data.tck.entities.embedded.HouseEntity;
import io.micronaut.data.tck.entities.embedded.HouseState;
import java.util.List;
@JdbcRepository(dialect = Dialect.POSTGRES)
interface TestRepository extends GenericRepository<HouseEntity, Long> {
    List<HouseEntity> findAllByResourceState(HouseState state);
}
@JdbcRepository(dialect = Dialect.POSTGRES)
interface OtherRepository extends GenericRepository<BookEntity, Long> {
    List<BookEntity> findAllByResourceState(BookState state);
}
""")
        then:
        noExceptionThrown()
    }

    @Unroll
    void "test build select with CTE for type #type"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect = Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query(\"""
            WITH ids AS (SELECT id FROM person)
            SELECT * FROM person
            \""")
    $type customSelect(Long id);

    @Query(\"""
           SELECT * FROM person WHERE id = :id FOR
           UPDATE
           \""")
    $type selectForUpdate(Long id);

}
""")
        def customSelectMethod = repository.findPossibleMethods("customSelect").findFirst().get()
        def customSelectQuery = getQuery(customSelectMethod)
        def selectForUpdateMethod = repository.findPossibleMethods("selectForUpdate").findFirst().get()
        def selectForUpdateQuery = getQuery(selectForUpdateMethod)

        expect:
        customSelectQuery.replace('\n', ' ') == "WITH ids AS (SELECT id FROM person) SELECT * FROM person "
        customSelectMethod.classValue(DataMethod, "interceptor").get() == interceptor

        selectForUpdateQuery.replace('\n', ' ') == "SELECT * FROM person WHERE id = :id FOR UPDATE "
        selectForUpdateMethod.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Person>'                      | FindAllInterceptor
        'Person'                                      | FindOneInterceptor
    }

    void "test build insert returning update on conflict"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends CrudRepository<Book, Long> {

    @Query("insert into book (id, title, total_pages) values (:id, :title, :totalPages) on conflict do update set title = :title returning id, title, total_pages")
    Book insertCustom(Long id, String title, int totalPages);

}
""")
        def insertCustomMethod = repository.findPossibleMethods("insertCustom").findFirst().get()
        def insertCustomQuery = getQuery(insertCustomMethod)

        expect:
        insertCustomMethod.classValue(DataMethod, "interceptor").get() == InsertReturningOneInterceptor
        insertCustomQuery == 'insert into book (id, title, total_pages) values (:id, :title, :totalPages) on conflict do update set title = :title returning id, title, total_pages'
    }

    void "test find by embedded property"() {
        given:
            def repository = buildRepository('test.RestaurantRepository', """
import io.micronaut.data.annotation.By;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface RestaurantRepository extends GenericRepository<Restaurant, Long> {

    Optional<Restaurant> find(@By("address.street") String street);
}

""")

            def method = repository.getRequiredMethod("find", String)
        expect:
            getQuery(method) == 'SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`street`,restaurant_.`zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM `restaurant` restaurant_ WHERE (restaurant_.`street` = ?)'
            getParameterBindingIndexes(method) == ["0"] as String[]
            getParameterBindingPaths(method) == [""] as String[]
            getParameterPropertyPaths(method) == ["address.street"] as String[]
    }

    void "test custom query raw matcher with quoted key words"() {
        given:
        def repository = buildRepository('test.ProductRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Product;

@JdbcRepository(dialect = Dialect.H2)
interface ProductRepository extends GenericRepository<Product, Long> {

    @Query("SELECT CASE WHEN p.price < 10 THEN 'INSERT' WHEN p.price => 20 THEN 'DELETE' ELSE 'UPDATE' END AS operation FROM product p WHERE p.id = :id /* Testing reserved words insert, update, delete in SQL comments */")
    String getStockOperation(Long id);

    @Query("SELECT CASE WHEN p.price < 10 THEN 'EXTENDED INSERT' WHEN p.price => 20 THEN 'EXTENDED DELETE' ELSE 'EXTENDED UPDATE' END AS operation FROM product p")
    List<String> getExtendedStockOperations();

    @Query("SELECT 'customValue' -- Just to test INSERT/UPDATE/DELETE in the SQL comment")
    String selectCustomString();
}
""")
        def getStockOperationMethod = repository.getRequiredMethod("getStockOperation", Long)
        def getExtendedStockOperationsMethod = repository.getRequiredMethod("getExtendedStockOperations")
        def selectCustomStringMethod = repository.getRequiredMethod("selectCustomString")

        expect:
        getStockOperationMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
        getExtendedStockOperationsMethod.classValue(DataMethod, "interceptor").get() == FindAllInterceptor
        selectCustomStringMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
    }

    void "test oracle raw query ignores lowercase returning in string literal"() {
        given:
        def repository = buildRepository('test.ProductRepository', """
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Product;

@JdbcRepository(dialect = Dialect.ORACLE)
interface ProductRepository extends GenericRepository<Product, Long> {

    @Query("SELECT 'user returning later' AS msg FROM dual")
    String message();
}
""")
        def method = repository.getRequiredMethod("message")

        expect:
        getRawQuery(method) == "SELECT 'user returning later' AS msg FROM dual"
        method.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
    }

    void "test oracle raw select keeps json returning clauses as query syntax"() {
        given:
        def repository = buildRepository('test.ProductRepository', """
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Product;

@JdbcRepository(dialect = Dialect.ORACLE)
interface ProductRepository extends GenericRepository<Product, Long> {

    @Query("SELECT json_serialize(json_object('id' VALUE p.id) RETURNING CLOB) FROM product p WHERE p.id = :id")
    String serializeProduct(Long id);

    @Query("SELECT json_arrayagg(p.name ORDER BY p.name RETURNING CLOB) FROM product p")
    String aggregateNames();

    @Query("SELECT json_serialize(json_object('action' VALUE 'needs update now' RETURNING CLOB) RETURNING CLOB) FROM product p")
    String serializeAction();
}
""")
        def serializeProductMethod = repository.getRequiredMethod("serializeProduct", Long)
        def aggregateNamesMethod = repository.getRequiredMethod("aggregateNames")
        def serializeActionMethod = repository.getRequiredMethod("serializeAction")

        expect:
        getRawQuery(serializeProductMethod) == "SELECT json_serialize(json_object('id' VALUE p.id) RETURNING CLOB) FROM product p WHERE p.id = ?"
        serializeProductMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
        getRawQuery(aggregateNamesMethod) == "SELECT json_arrayagg(p.name ORDER BY p.name RETURNING CLOB) FROM product p"
        aggregateNamesMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
        getRawQuery(serializeActionMethod) == "SELECT json_serialize(json_object('action' VALUE 'needs update now' RETURNING CLOB) RETURNING CLOB) FROM product p"
        serializeActionMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
    }

    void "test raw operation detection ignores nested and quoted keywords"() {
        given:
        def repository = buildRepository('test.RawKeywordRepository', """
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Product;

@JdbcRepository(dialect = Dialect.ORACLE)
interface RawKeywordRepository extends GenericRepository<Product, Long> {

    @Query("WITH \\"update\\" AS (SELECT p.id FROM product p WHERE p.name = 'needs update') SELECT json_serialize(json_object('id' VALUE p.id) RETURNING CLOB) FROM product p WHERE p.id = :id")
    String cteSelect(Long id);

    @Query("SELECT * FROM product WHERE id = :id FOR UPDATE")
    Product selectForUpdate(Long id);

    @Query("UPDATE book SET title = 'not returning'' yet' WHERE id = :id")
    int updateWithEscapedQuote(Long id);

    @Query("UPDATE book SET title = json_serialize(json_object('label' VALUE 'nested') RETURNING CLOB) WHERE id = :id")
    int updateWithNestedReturning(Long id);

    @Query("CALL read_something(:id)")
    String readProcedure(Long id);

    @Query(value = "CALL write_something(:id)", readOnly = false)
    int writeProcedure(Long id);
}
""")
        def cteSelectMethod = repository.getRequiredMethod("cteSelect", Long)
        def selectForUpdateMethod = repository.getRequiredMethod("selectForUpdate", Long)
        def updateWithEscapedQuoteMethod = repository.getRequiredMethod("updateWithEscapedQuote", Long)
        def updateWithNestedReturningMethod = repository.getRequiredMethod("updateWithNestedReturning", Long)
        def readProcedureMethod = repository.getRequiredMethod("readProcedure", Long)
        def writeProcedureMethod = repository.getRequiredMethod("writeProcedure", Long)

        expect:
        getOperationType(cteSelectMethod) == DataMethod.OperationType.QUERY
        cteSelectMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
        getRawQuery(cteSelectMethod) == "WITH \"update\" AS (SELECT p.id FROM product p WHERE p.name = 'needs update') SELECT json_serialize(json_object('id' VALUE p.id) RETURNING CLOB) FROM product p WHERE p.id = ?"

        getOperationType(selectForUpdateMethod) == DataMethod.OperationType.QUERY
        selectForUpdateMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
        getRawQuery(selectForUpdateMethod) == "SELECT * FROM product WHERE id = ? FOR UPDATE"

        getOperationType(updateWithEscapedQuoteMethod) == DataMethod.OperationType.UPDATE
        updateWithEscapedQuoteMethod.classValue(DataMethod, "interceptor").get() == UpdateInterceptor
        getRawQuery(updateWithEscapedQuoteMethod) == "UPDATE book SET title = 'not returning'' yet' WHERE id = ?"

        getOperationType(updateWithNestedReturningMethod) == DataMethod.OperationType.UPDATE
        updateWithNestedReturningMethod.classValue(DataMethod, "interceptor").get() == UpdateInterceptor
        getRawQuery(updateWithNestedReturningMethod) == "UPDATE book SET title = json_serialize(json_object('label' VALUE 'nested') RETURNING CLOB) WHERE id = ?"

        getOperationType(readProcedureMethod) == DataMethod.OperationType.QUERY
        readProcedureMethod.classValue(DataMethod, "interceptor").get() == FindOneInterceptor
        getRawQuery(readProcedureMethod) == "CALL read_something(?)"

        getOperationType(writeProcedureMethod) == DataMethod.OperationType.UPDATE
        writeProcedureMethod.classValue(DataMethod, "interceptor").get() == UpdateInterceptor
        getRawQuery(writeProcedureMethod) == "CALL write_something(?)"
    }

    void "test raw REPLACE INTO is treated as UPDATE (MySQL)"() {
        given:
        def repository = buildRepository('test.Repo', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.MYSQL)
interface Repo extends GenericRepository<Book, Long> {

    @Query("REPLACE INTO book (id, title, total_pages) VALUES (:id, :title, :totalPages)")
    int replaceCustom(Long id, String title, int totalPages);
}
""")
        def method = repository.getRequiredMethod("replaceCustom", Long, String, int)

        expect:
        getOperationType(method) == DataMethod.OperationType.UPDATE
        method.classValue(DataMethod, "interceptor").get() == UpdateInterceptor
        getRawQuery(method) == 'REPLACE INTO book (id, title, total_pages) VALUES (?, ?, ?)'
    }

    void "test raw INSERT with explicit parameters is treated as UPDATE operation"() {
        given:
        def repository = buildRepository('test.Repo', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.MYSQL)
interface Repo extends GenericRepository<Book, Long> {

    @Query("INSERT INTO book (title, total_pages) VALUES (:title, :totalPages)")
    int insertCustom(String title, int totalPages);
}
""")
        def method = repository.getRequiredMethod("insertCustom", String, int)

        expect:
        getOperationType(method) == DataMethod.OperationType.UPDATE
        method.classValue(DataMethod, "interceptor").get() == UpdateInterceptor
        getRawQuery(method) == 'INSERT INTO book (title, total_pages) VALUES (?, ?)'
    }

    void "test EmbeddedId naming strategy"() {
        given:
        def repository = buildRepository('test.SomeEntityRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.processor.entity.SomeEntity;
import jakarta.persistence.Embeddable;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.Optional;
@JdbcRepository(dialect = Dialect.H2)
interface SomeEntityRepository extends GenericRepository<SomeEntity, SomeEntity.PrimaryKey> {
    Optional<SomeEntity> findById(SomeEntity.PrimaryKey id);
    SomeEntity save(SomeEntity entity);
    List<SomeEntity> findAll();
}
""")

        def findByIdQuery = getQuery(repository.getRequiredMethod("findById", SomeEntity.PrimaryKey))
        def saveQuery = getQuery(repository.getRequiredMethod("save", SomeEntity))
        def findAllQuery = getQuery(repository.getRequiredMethod("findAll"))
        expect:
        findByIdQuery == 'SELECT some_entity_.`some_column`,some_entity_.`other_entity_id`,some_entity_.`col` FROM `some_table` some_entity_ WHERE (some_entity_.`some_column` = ? AND some_entity_.`other_entity_id` = ?)'
        saveQuery == 'INSERT INTO `some_table` (`col`,`some_column`,`other_entity_id`) VALUES (?,?,?)'
        findAllQuery == 'SELECT some_entity_.`some_column`,some_entity_.`other_entity_id`,some_entity_.`col` FROM `some_table` some_entity_'
    }

    void "test invalid embedded naming strategy fails compilation"() {
        when:
        withEmbeddedNamingStrategy("INVALID") {
            buildRepository('test.InvalidEmbeddedNamingRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@JdbcRepository(dialect = Dialect.H2)
interface InvalidEmbeddedNamingRepository extends GenericRepository<InvalidEmbeddedNamingEntity, Long> {
    InvalidEmbeddedNamingEntity save(InvalidEmbeddedNamingEntity entity);
}

@Entity
class InvalidEmbeddedNamingEntity {
    @Id
    Long id;
}
""")
        }

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Invalid value for 'micronaut.data.embedded.naming.strategy': INVALID")
        ex.message.contains("Supported values are LEGACY and STANDARD")
    }

    void "test legacy EmbeddedId naming strategy"() {
        given:
        def repository = withEmbeddedNamingStrategy("LEGACY") {
            buildRepository('test.LegacySomeEntityRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface LegacySomeEntityRepository extends GenericRepository<LegacySomeEntity, LegacySomeEntity.PrimaryKey> {
    Optional<LegacySomeEntity> findById(LegacySomeEntity.PrimaryKey id);
    LegacySomeEntity saveLegacy(LegacySomeEntity entity);
    List<LegacySomeEntity> findAll();
}

@Entity(name = "some_table")
@Table(name = "some_table")
record LegacySomeEntity(@EmbeddedId PrimaryKey primaryKey, String col) {
    @Embeddable
    record PrimaryKey(
            int someColumn,
            @ManyToOne LegacyOtherEntity otherEntity
    ) {
    }
}

@Entity(name = "other_table")
@Table(name = "other_table")
record LegacyOtherEntity(@Id @GeneratedValue Long id, String someColumn) {
}
""")
        }

        def findByIdQuery = getQuery(repository.executableMethods.find { it.methodName == "findById" && it.arguments.length == 1 && it.arguments[0].type.name.contains("PrimaryKey") })
        def saveQuery = getQuery(repository.executableMethods.find { it.methodName == "saveLegacy" })
        def findAllQuery = getQuery(repository.getRequiredMethod("findAll"))
        expect:
        findByIdQuery == 'SELECT legacy_some_entity_.`primary_key_some_column`,legacy_some_entity_.`primary_key_other_entity_id`,legacy_some_entity_.`col` FROM `some_table` legacy_some_entity_ WHERE (legacy_some_entity_.`primary_key_some_column` = ? AND legacy_some_entity_.`primary_key_other_entity_id` = ?)'
        saveQuery == 'INSERT INTO `some_table` (`col`,`primary_key_some_column`,`primary_key_other_entity_id`) VALUES (?,?,?)'
        findAllQuery == 'SELECT legacy_some_entity_.`primary_key_some_column`,legacy_some_entity_.`primary_key_other_entity_id`,legacy_some_entity_.`col` FROM `some_table` legacy_some_entity_'
    }
}
