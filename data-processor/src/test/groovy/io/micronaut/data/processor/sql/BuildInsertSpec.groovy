/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.model.entities.Person
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildInsertSpec extends AbstractDataSpec {

    void "test build SQL insert statement"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
interface MyInterface extends CrudRepository<Person, Long> {
}
""")

        expect:
        beanDefinition.getRequiredMethod("save", Person)
            .stringValue(DataMethod.class, DataMethod.META_MEMBER_INSERT_STMT)
            .orElse(null) == 'INSERT INTO "person" ("name","age","enabled") VALUES (?,?,?)'
    }
}
