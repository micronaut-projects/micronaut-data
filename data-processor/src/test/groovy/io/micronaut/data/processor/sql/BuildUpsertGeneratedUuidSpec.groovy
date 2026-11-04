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

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.BeanDefinition
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getOperationType
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

/**
 * MySQL cannot generate a UUID identity, so like an insert, an upsert must write the UUID the application provides.
 */
class BuildUpsertGeneratedUuidSpec extends AbstractDataSpec {

    @Unroll
    void "test build upsert with generated UUID identity and conflict properties for dialect - #dialect"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.MyInterface', """
import io.micronaut.data.annotation.*;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect=Dialect.${dialect.name()})
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Test, UUID> {

    Test save(Test test);

    @Upsert(conflictsOn = "name")
    Test put(Test test);

    @Upsert(conflictsOn = "name")
    List<Test> putAll(List<Test> tests);
}

@MappedEntity("upsert_uuid_test")
class Test {
    @Id
    @GeneratedValue
    private UUID id;
    private String name;
    private Integer pages;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }
}
""")

        when:
        def saveMethod = beanDefinition.findPossibleMethods("save").findFirst().get()
        def putMethod = beanDefinition.findPossibleMethods("put").findFirst().get()
        def putAllMethod = beanDefinition.findPossibleMethods("putAll").findFirst().get()

        then:
        getQuery(saveMethod) == insertQuery
        getOperationType(putMethod) == DataMethod.OperationType.UPSERT
        getQuery(putMethod) == upsertQuery
        getParameterPropertyPaths(putMethod) == parameterPropertyPaths as String[]
        getQuery(putAllMethod) == upsertQuery
        getParameterPropertyPaths(putAllMethod) == parameterPropertyPaths as String[]

        where:
        dialect          | insertQuery                                                                      | upsertQuery                                                                                                                       | parameterPropertyPaths
        Dialect.MYSQL    | 'INSERT INTO `upsert_uuid_test` (`name`,`pages`,`id`) VALUES (?,?,?)'            | 'INSERT INTO `upsert_uuid_test` (`name`,`pages`,`id`) VALUES (?,?,?) ON DUPLICATE KEY UPDATE `pages`=?'                           | ["name", "pages", "id", "pages"]
        Dialect.POSTGRES | 'INSERT INTO "upsert_uuid_test" ("name","pages") VALUES (?,?)'                    | 'INSERT INTO "upsert_uuid_test" ("name","pages") VALUES (?,?) ON CONFLICT ("name") DO UPDATE SET "pages"=EXCLUDED."pages"'         | ["name", "pages"]
    }
}
