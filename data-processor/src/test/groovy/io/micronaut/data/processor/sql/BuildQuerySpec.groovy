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
import io.micronaut.data.model.entities.Invoice
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Restaurant
import spock.lang.Issue
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.anyParameterExpandable
import static io.micronaut.data.processor.visitors.TestUtils.getCountQuery
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getJoins
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
        'findOne'               | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,alias_book_co_author_.`name` AS co_author_name,alias_book_co_author_.`nick_name` AS co_author_nick_name,au.`name` AS auname,au.`nick_name` AS aunick_name,alias_book_co_author_ob.`id` AS co_author_obid,alias_book_co_author_ob.`title` AS co_author_obtitle,alias_book_co_author_ob.`total_pages` AS co_author_obtotal_pages,alias_book_co_author_ob.`last_updated` AS co_author_oblast_updated,alias_book_co_author_ob.`author_id` AS co_author_obauthor_id,alias_book_co_author_ob.`co_author_id` AS co_author_obco_author_id FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` INNER JOIN `alias_author` alias_book_co_author_ ON alias_book_.`co_author_id`=alias_book_co_author_.`id` INNER JOIN `alias_book` alias_book_co_author_ob ON alias_book_co_author_.`id`=alias_book_co_author_ob.`author_id` WHERE (alias_book_.`id` = ?)'
        'findAliasBook'         | 'SELECT alias_book_.`id`,alias_book_.`title`,alias_book_.`total_pages`,alias_book_.`last_updated`,alias_book_.`author_id`,alias_book_.`co_author_id`,au_ob.`id` AS au_obid,au_ob.`title` AS au_obtitle,au_ob.`total_pages` AS au_obtotal_pages,au_ob.`last_updated` AS au_oblast_updated,au_ob.`author_id` AS au_obauthor_id,au_ob.`co_author_id` AS au_obco_author_id,au.`name` AS auname,au.`nick_name` AS aunick_name FROM `alias_book` alias_book_ INNER JOIN `alias_author` au ON alias_book_.`author_id`=au.`id` INNER JOIN `alias_book` au_ob ON au.`id`=au_ob.`author_id` WHERE (alias_book_.`id` = ?)'
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
        query.contains('ON meal_.`mid`=meal_foods_.`fk_meal_id` WHERE (meal_.`mid` = ? AND (meal_.actual = \'Y\' AND meal_foods_.fresh = \'Y\'))')

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
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
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
        query == 'SELECT food_.`fid`,food_.`key`,food_.`carbohydrates`,food_.`portion_grams`,food_.`created_on`,food_.`updated_on`,food_.`fk_meal_id`,food_.`fk_alt_meal`,food_.`loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name` AS ln,food_.`fresh`,food_meal_.`current_blood_glucose` AS meal_current_blood_glucose,food_meal_.`created_on` AS meal_created_on,food_meal_.`updated_on` AS meal_updated_on,food_meal_.`actual` AS meal_actual FROM `food` food_ INNER JOIN `meal` food_meal_ ON food_.`fk_meal_id`=food_meal_.`mid` WHERE (food_.`fid` = ? AND (food_.fresh = \'Y\' AND food_meal_.actual = \'Y\'))'
        queryFind == 'SELECT food_.`fid`,food_.`key`,food_.`carbohydrates`,food_.`portion_grams`,food_.`created_on`,food_.`updated_on`,food_.`fk_meal_id`,food_.`fk_alt_meal`,food_.`loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name` AS ln,food_.`fresh` FROM `food` food_ WHERE (food_.`fid` = ? AND (food_.fresh = \'Y\'))'

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
            getQuery(countMethod) == 'SELECT COUNT(*) FROM `meal` meal_ INNER JOIN `food` meal_foods_ ON meal_.`mid`=meal_foods_.`fk_meal_id` INNER JOIN `meal` meal_foods_alternative_meal_ ON meal_foods_.`fk_alt_meal`=meal_foods_alternative_meal_.`mid` WHERE (meal_foods_alternative_meal_.`current_blood_glucose` IN (?) AND (meal_.actual = \'Y\' AND meal_foods_alternative_meal_.actual = \'Y\'))'
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
        query.contains('WHERE ((meal_.`current_blood_glucose` >= ? AND meal_.`current_blood_glucose` <= ?) AND (meal_.actual = \'Y\'))')

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
import io.micronaut.data.tck.entities.Restaurant;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
interface RestaurantRepository extends GenericRepository<Restaurant, Long> {

    Optional<Restaurant> findByName(String name);

    Restaurant save(Restaurant entity);

    Restaurant findByAddressStreet(String street);

    String getMaxAddressStreetByName(String name);
}

""")

        def findByNameQuery = getQuery(repository.getRequiredMethod("findByName", String))
        def saveQuery = getQuery(repository.getRequiredMethod("save", Restaurant))
        def findByAddressStreetQuery = getQuery(repository.getRequiredMethod("findByAddressStreet", String))
        def getMaxAddressStreetByNameQuery = getQuery(repository.getRequiredMethod("getMaxAddressStreetByName", String))
        expect:
        findByNameQuery == 'SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`address_street`,restaurant_.`address_zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM `restaurant` restaurant_ WHERE (restaurant_.`name` = ?)'
        saveQuery == 'INSERT INTO `restaurant` (`name`,`address_street`,`address_zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
        findByAddressStreetQuery == 'SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`address_street`,restaurant_.`address_zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM `restaurant` restaurant_ WHERE (restaurant_.`address_street` = ?)'
        getMaxAddressStreetByNameQuery == 'SELECT MAX(restaurant_.`address_street`) FROM `restaurant` restaurant_ WHERE (restaurant_.`name` = ?)'
    }

    void "test count query with joins"() {
        given:
        def repository = buildRepository('test.AuthorRepository', """
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect = Dialect.H2)
interface AuthorRepository extends GenericRepository<Author, Long> {

    @Join(value = "books", type = Join.Type.FETCH)
    Page<Author> findAll(Pageable pageable);
}

""")

        def method = repository.getRequiredMethod("findAll", Pageable)
        def query = getQuery(method)
        def countQuery = getCountQuery(method)

        expect:
        query == 'SELECT author_.`id`,author_.`name`,author_.`nick_name`,author_books_.`id` AS books_id,author_books_.`author_id` AS books_author_id,author_books_.`genre_id` AS books_genre_id,author_books_.`title` AS books_title,author_books_.`total_pages` AS books_total_pages,author_books_.`publisher_id` AS books_publisher_id,author_books_.`last_updated` AS books_last_updated FROM `author` author_ INNER JOIN `book` author_books_ ON author_.`id`=author_books_.`author_id`'
        countQuery == 'SELECT COUNT(*) FROM `author` author_'

    }
}
