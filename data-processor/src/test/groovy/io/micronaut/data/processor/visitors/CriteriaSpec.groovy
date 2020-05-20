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
package io.micronaut.data.processor.visitors

import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import spock.lang.Unroll

class CriteriaSpec extends AbstractDataSpec {

    @Unroll
    void "test #criterion criterion produces the correct query - comparison"() {
        given:
        String methodName = "findBy${NameUtils.capitalize(property)}${criterion}"
        String sig = signature.entrySet().collect { "$it.value.name $it.key" }.join(",")
        BeanDefinition beanDefinition = buildRepository('test.MyInterface', """
import io.micronaut.data.model.entities.Person;

@Repository
interface MyInterface {
    Person $methodName($sig);    
}

""")

        def method = beanDefinition.getRequiredMethod(methodName, signature.values() as Class[])
        String query = method.synthesize(Query)value()

        expect: "The query is valid"
        query == "SELECT $alias FROM $Person.name AS $alias WHERE " + expectedQuery

        where: "The criterion is"
        alias   |property  | criterion           | signature        | expectedQuery
        alias() |"enabled" | "True"              | [:]              | "(${alias}.$property = TRUE )"
        alias() |"enabled" | "False"             | [:]              | "(${alias}.$property = FALSE )"
        alias() |"name"    | "IsNull"            | [:]              | "(${alias}.$property IS NULL )"
        alias() |"name"    | "IsNotNull"         | [:]              | "(${alias}.$property IS NOT NULL )"
        alias() |"name"    | "IsEmpty"           | [:]              | "(${alias}.name IS NULL OR ${alias}.name = '' )"
        alias() |"name"    | "IsNotEmpty"        | [:]              | "(${alias}.name IS NOT NULL AND ${alias}.name <> '' )"
        alias() |"age"     | "NotEqual"          | ["age": Integer] | "(${alias}.$property != :p1)"
        alias() |"age"     | "GreaterThan"       | ["age": Integer] | "(${alias}.$property > :p1)"
        alias() |"age"     | "NotGreaterThan"    | ["age": Integer] | "( NOT(${alias}.$property > :p1))"
        alias() |"age"     | "After"             | ["age": Integer] | "(${alias}.$property > :p1)"
        alias() |"age"     | "GreaterThanEquals" | ["age": Integer] | "(${alias}.$property >= :p1)"
        alias() |"age"     | "LessThan"          | ["age": Integer] | "(${alias}.$property < :p1)"
        alias() |"age"     | "Before"            | ["age": Integer] | "(${alias}.$property < :p1)"
        alias() |"age"     | "LessThanEquals"    | ["age": Integer] | "(${alias}.$property <= :p1)"
        alias() |"name"    | "Like"              | ["name": String] | "(${alias}.$property like :p1)"
        alias() |"name"    | "Ilike"             | ["name": String] | "(lower(${alias}.$property) like lower(:p1))"
        alias() |"name"    | "In"                | ["name": String] | "(${alias}.$property IN (:p1))"
        alias() |"name"    | "NotIn"             | ["name": String] | "( NOT(${alias}.$property IN (:p1)))"
        alias() |"name"    | "InList"            | ["name": String] | "(${alias}.$property IN (:p1))"
        alias() |"name"    | "StartsWith"        | ["name": String] | "(${alias}.$property LIKE CONCAT(:p1,'%'))"
        alias() |"name"    | "EndsWith"          | ["name": String] | "(${alias}.$property LIKE CONCAT('%',:p1))"
        alias() |"name"    | "StartingWith"      | ["name": String] | "(${alias}.$property LIKE CONCAT(:p1,'%'))"
        alias() |"name"    | "EndingWith"        | ["name": String] | "(${alias}.$property LIKE CONCAT('%',:p1))"
        alias() |"name"    | "Contains"          | ["name": String] | "(${alias}.$property LIKE CONCAT('%',:p1,'%'))"
        alias() |"name"    | "Containing"        | ["name": String] | "(${alias}.$property LIKE CONCAT('%',:p1,'%'))"
    }

    private String alias() {
        new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))
    }
}
