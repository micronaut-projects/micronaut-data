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
package io.micronaut.data.processor.visitors

import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.entities.Person

import io.micronaut.inject.BeanDefinition
import spock.lang.Unroll

class CriteriaSpec extends AbstractPredatorSpec {

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
        query == "SELECT person FROM $Person.name AS person WHERE " + expectedQuery

        where: "The criterion is"
        property  | criterion           | signature        | expectedQuery
        "enabled" | "True"              | [:]              | "(person.$property = TRUE )"
        "enabled" | "False"             | [:]              | "(person.$property = FALSE )"
        "name"    | "IsNull"            | [:]              | "(person.$property IS NULL )"
        "name"    | "IsNotNull"         | [:]              | "(person.$property IS NOT NULL )"
        "name"    | "IsEmpty"           | [:]              | "(person.name IS NULL OR person.name = '' )"
        "name"    | "IsNotEmpty"        | [:]              | "(person.name IS NOT NULL AND person.name <> '' )"
        "age"     | "NotEqual"          | ["age": Integer] | "(person.$property != :p1)"
        "age"     | "GreaterThan"       | ["age": Integer] | "(person.$property > :p1)"
        "age"     | "NotGreaterThan"    | ["age": Integer] | "( NOT(person.$property > :p1))"
        "age"     | "After"             | ["age": Integer] | "(person.$property > :p1)"
        "age"     | "GreaterThanEquals" | ["age": Integer] | "(person.$property >= :p1)"
        "age"     | "LessThan"          | ["age": Integer] | "(person.$property < :p1)"
        "age"     | "Before"            | ["age": Integer] | "(person.$property < :p1)"
        "age"     | "LessThanEquals"    | ["age": Integer] | "(person.$property <= :p1)"
        "name"    | "Like"              | ["name": String] | "(person.$property like :p1)"
        "name"    | "Ilike"             | ["name": String] | "(lower(person.$property) like lower(:p1))"
        "name"    | "In"                | ["name": String] | "(person.$property IN (:p1))"
        "name"    | "NotIn"             | ["name": String] | "( NOT(person.$property IN (:p1)))"
        "name"    | "InList"            | ["name": String] | "(person.$property IN (:p1))"
        "name"    | "StartsWith"        | ["name": String] | "(person.$property LIKE CONCAT(:p1,'%'))"
        "name"    | "EndsWith"          | ["name": String] | "(person.$property LIKE CONCAT('%',:p1))"
        "name"    | "StartingWith"      | ["name": String] | "(person.$property LIKE CONCAT(:p1,'%'))"
        "name"    | "EndingWith"        | ["name": String] | "(person.$property LIKE CONCAT('%',:p1))"
        "name"    | "Contains"          | ["name": String] | "(person.$property LIKE CONCAT('%',:p1,'%'))"
        "name"    | "Containing"        | ["name": String] | "(person.$property LIKE CONCAT('%',:p1,'%'))"
    }
}
