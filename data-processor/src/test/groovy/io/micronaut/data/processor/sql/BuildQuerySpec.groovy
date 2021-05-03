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

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Issue
import spock.lang.Unroll

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
            String query = repository.getRequiredMethod("findById", Long).stringValue(Query).get()

        then:
            query == 'SELECT custom_book_."id",custom_book_."title" FROM "CustomBooK" custom_book_ WHERE (custom_book_."id" = ?)'
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
        String query = repository.getRequiredMethod("findAuthorById", Long).stringValue(Query).get()

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

        def query = repository.getRequiredMethod("searchById", Long)
                .stringValue(Query).get()

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

        def query = repository.getRequiredMethod("queryById", UUID)
                .stringValue(Query).get()

        expect:
        query == 'SELECT food_.`fid`,food_.`key`,food_.`carbohydrates`,food_.`portion_grams`,food_.`created_on`,food_.`updated_on`,food_.`fk_meal_id`,food_.`fk_alt_meal`,food_meal_.`current_blood_glucose` AS meal_current_blood_glucose,food_meal_.`created_on` AS meal_created_on,food_meal_.`updated_on` AS meal_updated_on FROM `food` food_ INNER JOIN `meal` food_meal_ ON food_.`fk_meal_id`=food_meal_.`mid` WHERE (food_.`fid` = ?)'

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
        method.stringValue(Query).get() == query

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
        def query = method
                .stringValue(Query)
                .get()


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

            def query = repository.getRequiredMethod("countDistinctByFoodsAlternativeMealCurrentBloodGlucoseInList", List)
                    .stringValue(Query).get()

        expect:
            query == 'SELECT COUNT(*) FROM `meal` meal_ INNER JOIN `food` meal_foods_ ON meal_.`mid`=meal_foods_.`fk_meal_id` INNER JOIN `meal` meal_foods_alternative_meal_ ON meal_foods_.`fk_alt_meal`=meal_foods_alternative_meal_.`mid` WHERE (meal_foods_alternative_meal_.`current_blood_glucose` IN (?))'

    }


}
