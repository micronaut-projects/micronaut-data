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

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.DataType
import io.micronaut.data.model.Pageable
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Issue
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.anyParameterExpandable
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery
import static io.micronaut.data.processor.visitors.TestUtils.getRawQuery
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
@io.micronaut.context.annotation.Executable
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
@io.micronaut.context.annotation.Executable
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
}
"""
        )

        when:
        String query = getQuery(repository.getRequiredMethod("findAuthorById", Long))

        then:
        query == 'SELECT book_author_.`id`,book_author_.`name`,book_author_.`nick_name` FROM `book` book_ INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` = ?)'

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
        query.contains('ON meal_.`mid`=meal_foods_.`fk_meal_id` WHERE (meal_.`mid` = ?)')

    }

    void "test join query with custom foreign key"() {
        given:
        def repository = buildRepository('test.FoodRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Food;
import java.util.Optional;
import java.util.UUID;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface FoodRepository extends CrudRepository<Food, UUID> {

    @Join("meal")
    Optional<Food> queryById(UUID uuid);
}
""")

        def query = getQuery(repository.getRequiredMethod("queryById", UUID))

        expect:
        query == 'SELECT food_.`fid`,food_.`key`,food_.`carbohydrates`,food_.`portion_grams`,food_.`created_on`,food_.`updated_on`,food_.`fk_meal_id`,food_.`fk_alt_meal`,food_meal_.`current_blood_glucose` AS meal_current_blood_glucose,food_meal_.`created_on` AS meal_created_on,food_meal_.`updated_on` AS meal_updated_on FROM `food` food_ INNER JOIN `meal` food_meal_ ON food_.`fk_meal_id`=food_meal_.`mid` WHERE (food_.`fid` = ?)'

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
        "existsById"       | 'SELECT TRUE FROM `custom_id_entity` custom_id_entity_ WHERE (custom_id_entity_.`id` = ?)'
        "findById"         | 'SELECT custom_id_entity_.`custom_id`,custom_id_entity_.`id`,custom_id_entity_.`name` FROM `custom_id_entity` custom_id_entity_ WHERE (custom_id_entity_.`id` = ?)'

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
            getQuery(countMethod) == 'SELECT COUNT(*) FROM `meal` meal_ INNER JOIN `food` meal_foods_ ON meal_.`mid`=meal_foods_.`fk_meal_id` INNER JOIN `meal` meal_foods_alternative_meal_ ON meal_foods_.`fk_alt_meal`=meal_foods_alternative_meal_.`mid` WHERE (meal_foods_alternative_meal_.`current_blood_glucose` IN (?))'
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
            def repository = buildRepository('test.UserRoleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.jdbc.entities.Role;
import io.micronaut.data.tck.jdbc.entities.User;
import io.micronaut.data.tck.jdbc.entities.UserRole;
import io.micronaut.data.tck.jdbc.entities.UserRoleId;

@JdbcRepository(dialect= Dialect.MYSQL)
interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {

    @Join("role")
    Iterable<Role> findRoleByUser(User user);
}
""")

            def method = repository.findPossibleMethods("findRoleByUser").findAny().get()

        expect:
            getQuery(method) == 'SELECT user_role_id_role_.`id`,user_role_id_role_.`name` FROM `user_role_composite` user_role_ INNER JOIN `role_composite` user_role_id_role_ ON user_role_.`id_role_id`=user_role_id_role_.`id` WHERE (user_role_.`id_user_id` = ?)'
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
import io.micronaut.data.tck.entities.City;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CitiesRepository extends CrudRepository<City, Long> {

    @Join("countryRegion")
    @Join("countryRegion.country")
    int countDistinctByCountryRegionCountryUuid(UUID id);
}
""")
        def query = getQuery(repository.getRequiredMethod("countDistinctByCountryRegionCountryUuid", UUID))

        expect:
        query == 'SELECT COUNT(*) FROM `T_CITY` city_ INNER JOIN `CountryRegion` city_country_region_ ON city_.`country_region_id`=city_country_region_.`id` INNER JOIN `country` city_country_region_country_ ON city_country_region_.`countryId`=city_country_region_country_.`uuid` WHERE (city_country_region_country_.`uuid` = ?)'

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
        query == 'SELECT COUNT(*) FROM `face` face_ INNER JOIN `nose` face_nose_ ON face_.`id`=face_nose_.`face_id` WHERE (face_nose_.`id` = ?)'

    }

    void "test In in properties"() {
        given:
        def repository = buildRepository('test.PurchaseRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.entities.Purchase;
import io.micronaut.data.tck.entities.Face;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PurchaseRepository extends CrudRepository<Purchase, Long> {

    @Join("invoice")
    Purchase findByNameAndInvoiceId(String n, Long id);

    Purchase findByCustomerIdAndShouldReceiveCopyOfInvoiceTrue(Long id, Boolean should);
}
""")
        def query1 = getQuery(repository.getRequiredMethod("findByNameAndInvoiceId", String, Long))
        def query2 = getQuery(repository.getRequiredMethod("findByCustomerIdAndShouldReceiveCopyOfInvoiceTrue", Long, Boolean))

        expect:
        query1 == 'SELECT purchase_.`id`,purchase_.`version`,purchase_.`name`,purchase_.`invoice_id`,purchase_.`customer_id`,purchase_.`should_receive_copy_of_invoice`,purchase_invoice_.`version` AS invoice_version,purchase_invoice_.`name` AS invoice_name FROM `purchase` purchase_ INNER JOIN `invoice` purchase_invoice_ ON purchase_.`invoice_id`=purchase_invoice_.`id` WHERE (purchase_.`name` = ? AND purchase_.`invoice_id` = ?)'
        query2 == 'SELECT purchase_.`id`,purchase_.`version`,purchase_.`name`,purchase_.`invoice_id`,purchase_.`customer_id`,purchase_.`should_receive_copy_of_invoice` FROM `purchase` purchase_ WHERE (purchase_.`customer_id` = ? AND purchase_.`should_receive_copy_of_invoice` = TRUE)'

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
        query.contains('WHERE ((meal_.`current_blood_glucose` >= ? AND meal_.`current_blood_glucose` <= ?))')

    }
}
