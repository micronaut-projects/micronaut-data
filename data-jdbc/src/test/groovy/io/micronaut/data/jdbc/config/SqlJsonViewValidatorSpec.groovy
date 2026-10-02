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
package io.micronaut.data.jdbc.config

import io.micronaut.data.annotation.JsonView
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationResult
import io.micronaut.data.model.query.builder.sql.validation.SqlJsonViewValidator
import io.micronaut.data.model.schema.sql.SqlJsonViewMapping
import io.micronaut.data.model.schema.sql.metadata.SqlJsonViewMetadata
import spock.lang.Specification

import static io.micronaut.data.annotation.JsonView.Operation.DELETE
import static io.micronaut.data.annotation.JsonView.Operation.INSERT
import static io.micronaut.data.annotation.JsonView.Operation.UPDATE

class SqlJsonViewValidatorSpec extends Specification {

    static final Set<JsonView.Operation> ALL = [INSERT, UPDATE, DELETE] as Set

    // student { _id, address { street } (object), classes [ { id } ] (array) }
    static final SqlJsonViewMapping STUDENT = new SqlJsonViewMapping(null, 'student_view', table('STUDENT', null, false, ALL, ['_id': 'ID'], [
            table('ADDRESS', 'address', false, [INSERT, UPDATE] as Set, ['street': 'STREET'], []),
            table('CLASSES', 'classes', true, ALL, ['id': 'ID'], [])
    ]))

    void 'matching view has no differences'() {
        expect:
        with(validate(STUDENT, studentView())) {
            errors.isEmpty()
            warnings.isEmpty()
        }
    }

    void 'sub view operations are compared'() {
        given:"The address table allows deletes"
        def view = studentView(tables: [
                dbTable(0, null, 'STUDENT', null, ALL),
                dbTable(1, 0, 'ADDRESS', 'singleton', ALL),
                dbTable(2, 0, 'CLASSES', 'nested', ALL)])

        expect:
        with(validate(STUDENT, view)) {
            errors.isEmpty()
            warnings == ['JSON view [student_view] sub view [address] allows operations [UPDATE, INSERT, DELETE] but the view entity declares [UPDATE, INSERT]']
        }
    }

    void 'sub view under a different key or with a different relationship is an error'() {
        given:"The address under another key and the classes as an object"
        def view = studentView(
                tables: [
                        dbTable(0, null, 'STUDENT', null, ALL),
                        dbTable(1, 0, 'ADDRESS', 'singleton', [INSERT, UPDATE] as Set),
                        dbTable(2, 0, 'CLASSES', 'singleton', ALL)],
                links: [link('STUDENT', 'ADDRESS', 'homeAddress'), link('STUDENT', 'CLASSES', 'classes')])

        expect:
        with(validate(STUDENT, view)) {
            errors == [
                    'JSON view [student_view] sub view [address] of table [ADDRESS] not found',
                    'JSON view [student_view] sub view [classes] of table [CLASSES] is an object but the view entity maps it as an array'
            ]
            warnings == ['JSON view [student_view] sub view [homeAddress] of table [ADDRESS] is not mapped by the view entity']
        }
    }

    void 'the same table used twice in the view is matched by its fields'() {
        given:"The home and work addresses from the same table with different fields"
        def mapping = new SqlJsonViewMapping(null, 'person_view', table('PERSON', null, false, ALL, ['_id': 'ID'], [
                table('ADDRESS', 'home', false, ALL, ['street': 'STREET'], []),
                table('ADDRESS', 'work', false, ALL, ['city': 'CITY'], [])
        ]))
        def view = new SqlJsonViewMetadata('PERSON_VIEW', 'VALID',
                [dbTable(0, null, 'PERSON', null, ALL), dbTable(1, 0, 'ADDRESS', 'singleton', ALL), dbTable(2, 0, 'ADDRESS', 'singleton', ALL)],
                [field(0, '_id', 'ID'), field(1, 'city', 'CITY'), field(2, 'street', 'STREET')],
                [link('PERSON', 'ADDRESS', 'home'), link('PERSON', 'ADDRESS', 'work')])

        expect:
        with(validate(mapping, view)) {
            errors.isEmpty()
            warnings.isEmpty()
        }

        when:"A field of the second use is missing"
        view = new SqlJsonViewMetadata('PERSON_VIEW', 'VALID', view.tables(),
                [field(0, '_id', 'ID'), field(2, 'street', 'STREET')], view.links())

        then:
        validate(mapping, view).errors == ['JSON view [person_view] field [city] of table [ADDRESS] not found in sub view [work]']
    }

    void 'unnested sub view has no key'() {
        given:
        def mapping = new SqlJsonViewMapping(null, 'car_view', table('CAR', null, false, ALL, ['_id': 'ID'], [
                table('CAR_DETAILS', null, false, [INSERT, UPDATE] as Set, ['model': 'MODEL'], [])
        ]))
        def view = new SqlJsonViewMetadata('CAR_VIEW', 'VALID',
                [dbTable(0, null, 'CAR', null, ALL), dbTable(1, 0, 'CAR_DETAILS', 'singleton', [INSERT, UPDATE] as Set)],
                [field(0, '_id', 'ID'), field(1, 'model', 'MODEL')],
                [link('CAR', 'CAR_DETAILS', null)])

        expect:
        with(validate(mapping, view)) {
            errors.isEmpty()
            warnings.isEmpty()
        }
    }

    private static SchemaValidationResult validate(SqlJsonViewMapping mapping, SqlJsonViewMetadata metadata) {
        def result = new SchemaValidationResult()
        SqlJsonViewValidator.validate(mapping, metadata, result)
        return result
    }

    private static SqlJsonViewMetadata studentView(Map<String, Object> overrides = [:]) {
        return new SqlJsonViewMetadata('STUDENT_VIEW', 'VALID',
                overrides.tables as List ?: [
                        dbTable(0, null, 'STUDENT', null, ALL),
                        dbTable(1, 0, 'ADDRESS', 'singleton', [INSERT, UPDATE] as Set),
                        dbTable(2, 0, 'CLASSES', 'nested', ALL)],
                [field(0, '_id', 'ID'), field(1, 'street', 'STREET'), field(2, 'id', 'ID')],
                overrides.links as List ?: [link('STUDENT', 'ADDRESS', 'address'), link('STUDENT', 'CLASSES', 'classes')])
    }

    private static SqlJsonViewMapping.Table table(String name, String key, boolean nested, Set<JsonView.Operation> operations,
                                                  Map<String, String> fields, List<SqlJsonViewMapping.Table> children) {
        return new SqlJsonViewMapping.Table(name, key, nested, operations,
                fields.collect { new SqlJsonViewMapping.Field(name, it.key, it.value) }, children)
    }

    private static SqlJsonViewMetadata.Table dbTable(int number, Integer parentNumber, String name, String relationship,
                                                     Set<JsonView.Operation> operations) {
        return new SqlJsonViewMetadata.Table(number, parentNumber, name, relationship,
                operations.contains(INSERT), operations.contains(UPDATE), operations.contains(DELETE))
    }

    private static SqlJsonViewMetadata.Field field(int tableNumber, String key, String column) {
        return new SqlJsonViewMetadata.Field(tableNumber, key, column)
    }

    private static SqlJsonViewMetadata.Link link(String parent, String child, String key) {
        return new SqlJsonViewMetadata.Link(parent, child, key)
    }
}
