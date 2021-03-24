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
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Unroll

class BuildDeleteSpec extends AbstractDataSpec {

    @Unroll
    void "test build delete"() {
        given:
            def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Integer> {

    @Query("DELETE FROM movie WHERE title = :title")
    int deleteCustom(java.util.List<Movie> movies);

    @Query("DELETE FROM movie WHERE title = :title")
    int deleteCustomSingle(Movie movie);

}

${entity('Movie', [title: String, theLongName: String])}
""")
            def method = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
            method.stringValue(Query, "rawQuery").get() == query
            method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == bindingPaths
            method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == binding


        where:
            methodName           | query                               | bindingPaths          | binding
            'deleteCustom'       | 'DELETE FROM movie WHERE title = ?' | ['title'] as String[] | [] as String[]
            'deleteCustomSingle' | 'DELETE FROM movie WHERE title = ?' | ['title'] as String[] | [] as String[]
    }

}
