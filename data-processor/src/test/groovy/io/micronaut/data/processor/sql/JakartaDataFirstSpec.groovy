/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Restaurant

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JakartaDataFirstSpec extends AbstractDataSpec {

    void "test Jakarta Data @First on @Find and @Query methods"() {
        given:
        def repository = buildRepository('test.RestaurantRepoFirst', """
import jakarta.data.repository.Find;
import jakarta.data.repository.First;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoFirst {

    @Find
    @First(3)
    @OrderBy("name")
    Restaurant[] firstThreeArray();

    @Find
    @First(4)
    @OrderBy("name")
    List<Restaurant> firstFourList();

    @First(5)
    @Query("WHERE name IS NOT NULL ORDER BY name")
    List<Restaurant> queryFirstFive();

    @First
    @Query("WHERE name IS NOT NULL ORDER BY name")
    Optional<Restaurant> queryFirstOne();
}
""")

        when:
        def firstThreeArray = repository.getRequiredMethod("firstThreeArray")
        def firstFourList = repository.getRequiredMethod("firstFourList")
        def queryFirstFive = repository.getRequiredMethod("queryFirstFive")
        def queryFirstOne = repository.getRequiredMethod("queryFirstOne")

        then: "@Find with @First applies the limit and still returns all rows"
        getQuery(firstThreeArray).endsWith('ORDER BY restaurant_.`name` ASC LIMIT 3')
        getQuery(firstFourList).endsWith('ORDER BY restaurant_.`name` ASC LIMIT 4')

        and: "an array return type uses the find-all interceptor"
        firstThreeArray.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == FindAllInterceptor.name
        firstFourList.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == FindAllInterceptor.name

        and: "@Query with @First applies the limit too"
        getQuery(queryFirstFive).endsWith('LIMIT 5')
        getQuery(queryFirstOne).endsWith('LIMIT 1')
    }

    void "test an array return type on a criteria method returns all results"() {
        given:
        def repository = buildRepository('test.RestaurantRepoArray', '''
import jakarta.data.constraint.GreaterThan;
import jakarta.data.repository.Find;
import jakarta.data.repository.First;
import jakarta.data.repository.Is;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoArray {

    @Find
    @First(3)
    @OrderBy("name")
    Restaurant[] afterArray(@Is(GreaterThan.class) String name);

    @Find
    @OrderBy("name")
    List<Restaurant> afterList(@Is(GreaterThan.class) String name);
}
''')

        when: "a constraint parameter routes the method through the criteria path"
        def afterArray = repository.getRequiredMethod("afterArray", String)
        def afterList = repository.getRequiredMethod("afterList", String)

        then: "an array is a multi-result return type, just like a list"
        def arrayInterceptor = afterArray.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get()
        arrayInterceptor == afterList.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get()
        arrayInterceptor.endsWith('FindAllSpecificationInterceptor')
    }
}
