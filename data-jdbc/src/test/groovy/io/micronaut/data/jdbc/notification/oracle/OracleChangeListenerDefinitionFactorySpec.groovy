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
import oracle.jdbc.OracleConnection
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
        definition.registrationProperties().getProperty(OracleConnection.NTF_TIMEOUT) == '3600'
        definition.renewalPolicy().timeoutSeconds() == 3600
        definition.renewalPolicy().leadTimeSeconds() == 60
    }

    void "builds a query result registration query and preserves its Oracle properties"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification(
                [select : 'id, title', where: 'enabled = 1', timeoutSeconds: 120,
                 renewal: OracleChangeNotification.RenewalMode.AFTER_EXPIRATION],
                [
                        [name: OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, value: 'true'],
                        [name: OracleConnection.NTF_QOS_PURGE_ON_NTFN, value: 'true'],
                        [name: 'CUSTOM_PROPERTY', value: 'custom-value']
                ]
        ))

        when:
        def definition = new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        definition.registrationQuery() == 'SELECT id, title FROM "SALES"."ORDER.ITEMS" WHERE enabled = 1'
        definition.registrationProperties().getProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION) == 'true'
        definition.registrationProperties().getProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN) == 'true'
        definition.registrationProperties().getProperty('CUSTOM_PROPERTY') == 'custom-value'
        definition.registrationProperties().getProperty(OracleConnection.DCN_NOTIFY_ROWIDS) == 'true'
        definition.registrationProperties().getProperty(OracleConnection.NTF_TIMEOUT) == '180'
        definition.renewalPolicy().timeoutSeconds() == 120
        definition.renewalPolicy().mode() == OracleChangeNotification.RenewalMode.AFTER_EXPIRATION
        definition.renewalPolicy().serverTimeoutSeconds() == 180
        0 * operations.execute(_)
    }

    void "adds a server cleanup grace period for after-expiration registrations"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification([
                timeoutSeconds: 120,
                renewal      : OracleChangeNotification.RenewalMode.AFTER_EXPIRATION
        ]))

        when:
        def definition = new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        definition.renewalPolicy().timeoutSeconds() == 120
        definition.renewalPolicy().serverTimeoutSeconds() == 180
        definition.registrationProperties().getProperty(OracleConnection.NTF_TIMEOUT) == '180'
    }

    void "rejects select or where for object change notifications"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification([select: select, where: where]))

        when:
        new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains('may specify Oracle select or where only when')

        where:
        select | where
        'id'   | ''
        '*'    | 'enabled = 1'
    }

    void "rejects a blank select value"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification(
                [select: '   '],
                [[name: OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, value: 'true']]
        ))

        when:
        new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains('must have a non-blank Oracle select value')
    }

    void "rejects invalid timeout and overlapping renewal lead time"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification([
                timeoutSeconds        : timeoutSeconds,
                renewal               : OracleChangeNotification.RenewalMode.OVERLAPPING,
                renewalLeadTimeSeconds: renewalLeadTimeSeconds
        ]))

        when:
        new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains(expectedMessage)

        where:
        timeoutSeconds | renewalLeadTimeSeconds | expectedMessage
        0              | 60                     | 'requires timeoutSeconds to be greater than 0'
        60             | 0                      | 'requires renewalLeadTimeSeconds to be greater than 0'
        60             | 60                     | 'requires renewalLeadTimeSeconds to be greater than 0'
    }

    void "rejects invalid Oracle registration properties"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification([:], [[name: propertyName, value: propertyValue]]))

        when:
        new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains(expectedMessage)

        where:
        propertyName                          | propertyValue | expectedMessage
        ''                                    | 'value'       | 'has an Oracle property with a blank name'
        OracleConnection.DCN_NOTIFY_CHANGELAG | '1'           | 'requires ' + OracleConnection.DCN_NOTIFY_CHANGELAG
        OracleConnection.NTF_TIMEOUT          | '10'          | 'must configure Oracle registration timeout with timeoutSeconds'
    }

    void "requires Oracle change notification configuration"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(null)

        when:
        new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        def exception = thrown(NullPointerException)
        exception.message.contains('requires @OracleChangeNotification for an Oracle datasource')
    }

    void "requires the generated Oracle ROWID reload query"() {
        given:
        def operations = operations()
        def listenerMethod = listenerMethod(notification(), Optional.empty())

        when:
        new OracleChangeListenerDefinitionFactory(operations).create(listenerMethod)

        then:
        def exception = thrown(IllegalStateException)
        exception.message.contains('is missing its generated Oracle ROWID reload query')
    }

    private JdbcRepositoryOperations operations() {
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(SchemaBook) >> new RuntimePersistentEntity<>(SchemaBook)
        operations.conversionService >> ConversionService.SHARED
        operations
    }

    private ChangeListenerMethod listenerMethod(AnnotationValue notification,
                                                Optional reloadQuery = Optional.of('SELECT * FROM "SALES"."ORDER.ITEMS" WHERE ROWID = ?')) {
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void onChange(ChangeEvent<SchemaBook>)'
        method.getAnnotation(OracleChangeNotification) >> notification
        method.stringValue(OracleChangeListenerQuery) >> reloadQuery
        new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(SchemaBook))
    }

    private static AnnotationValue notification(Map<String, Object> members = [:],
                                                List<Map<String, String>> properties = []) {
        def builder = AnnotationValue.builder(OracleChangeNotification)
        members.each { name, value ->
            builder.member(name, value)
        }
        if (!properties.isEmpty()) {
            def propertyValues = properties.collect { property ->
                AnnotationValue.builder(OracleChangeNotification.Property)
                        .member('name', property.name)
                        .member('value', property.value)
                        .build()
            } as AnnotationValue[]
            builder.member('properties', propertyValues)
        }
        builder.build()
    }

    @MappedEntity(value = "order.items", schema = "Sales")
    private static class SchemaBook {
        @Id
        Long id
    }
}
