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
package io.micronaut.data.jdbc.notification.oracle

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class OracleChangeListenerDefinitionFactorySpec extends Specification {

    void "uses the mapped Oracle identifier for registration and notification matching"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        def persistentEntity = new RuntimePersistentEntity<>(SchemaBook)
        operations.getEntity(SchemaBook) >> persistentEntity
        operations.conversionService >> ConversionService.SHARED
        def method = Mock(ExecutableMethod)
        method.getAnnotation(OracleChangeNotification) >> AnnotationValue.builder(OracleChangeNotification).build()
        method.stringValue(OracleChangeListenerQuery) >> Optional.of('SELECT * FROM "SALES"."ORDER.ITEMS" WHERE ROWID = ?')
        def listenerMethod = new ChangeListenerMethod(
            Mock(BeanDefinition),
            method,
            Argument.of(SchemaBook)
        )

        when:
        def definition = new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        definition.tableIdentifier().sqlName() == '"SALES"."ORDER.ITEMS"'
        definition.registrationQuery() == 'SELECT * FROM "SALES"."ORDER.ITEMS"'
        definition.tableIdentifier().matches('"SALES"."ORDER.ITEMS"')
        !definition.tableIdentifier().matches('"OTHER"."ORDER.ITEMS"')
    }

    @MappedEntity(value = "order.items", schema = "Sales")
    private static class SchemaBook {
        @Id
        Long id
    }
}
