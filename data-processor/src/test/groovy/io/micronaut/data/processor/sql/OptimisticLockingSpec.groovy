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

class OptimisticLockingSpec extends AbstractDataSpec {

    void "test optimistic locking methods"() {
        given:
            def repository = buildRepository('test.StudentRepository', """
  import io.micronaut.data.jdbc.annotation.JdbcRepository;
  import io.micronaut.data.model.query.builder.sql.Dialect;
  import io.micronaut.data.tck.entities.Student;
  @JdbcRepository(dialect= Dialect.MYSQL)
  @io.micronaut.context.annotation.Executable
  interface StudentRepository extends CrudRepository<Student, Long> {
    void updateByIdAndVersion(Long id, Long version, String name);
    
    void updateStudent1(@Id Long id, @Version Long zzz, String name);
    void updateStudent2(@Id Long id, @Version Long version, String name);
    void updateStudent3(@Id Long id, String name);
    
    void updateById(@Id Long id, @Version Long version, String name);
    void update(@Id Long id, @Version Long version, String name);
    void delete(@Id Long id, @Version Long version, String name);
    
    void deleteByIdAndVersionAndName(Long id, Long version, String name);
  }
  """)

        when:
            def updateByIdAndVersionMethod = repository.findPossibleMethods("updateByIdAndVersion").findFirst().get()
            def updateByIdAndVersionQuery = updateByIdAndVersionMethod.stringValue(Query).get()

        then:
            updateByIdAndVersionQuery == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            updateByIdAndVersionMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ['', '', '', '', ''] as String[]
            updateByIdAndVersionMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['2', '-1', '-1', '0', '1'] as String[]
            updateByIdAndVersionMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PROPERTY_PATHS) == ["", "lastUpdatedTime", "version", "", ""] as String[]
            updateByIdAndVersionMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS) == ["", "", "", "", ""] as String[]
            updateByIdAndVersionMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_INDEXES) == ["-1", "-1", "1", "-1", "-1"] as String[]
            updateByIdAndVersionMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK).get()

            def updateOneMethod = repository.findPossibleMethods("update").filter({ it -> it.getArguments().size() == 1 }).findFirst().get()
            def updateOneQuery = updateOneMethod.stringValue(Query).get()

        then:
            updateOneQuery == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            updateOneMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ["name", "lastUpdatedTime", "version", "id", ''] as String[]
            updateOneMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS) == ["", "", "", "", "version"] as String[]
            updateOneMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def updateStudentMethod1 = repository.findPossibleMethods("updateStudent1").findFirst().get()
            def updateStudentQuery1 = updateStudentMethod1.stringValue(Query).get()

        then:
            updateStudentQuery1 == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            updateStudentMethod1.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ['', '', '', '', ''] as String[]
            updateStudentMethod1.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['2', '-1', '-1', '0', "1"] as String[]
            updateStudentMethod1.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PROPERTY_PATHS) == ["", "lastUpdatedTime", "version", "", ""] as String[]
            updateStudentMethod1.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS) == ["", "", "", "", ""] as String[]
            updateStudentMethod1.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_INDEXES) == ["-1", "-1", "1", "-1", "-1"] as String[]
            updateStudentMethod1.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def updateStudentMethod2 = repository.findPossibleMethods("updateStudent2").findFirst().get()
            def updateStudentQuery2 = updateStudentMethod2.stringValue(Query).get()

        then:
            updateStudentQuery2 == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            updateStudentMethod2.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ['', "", "", "", ""] as String[]
            updateStudentMethod2.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['2', '-1', '-1', '0', "1"] as String[]
            updateStudentMethod2.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PROPERTY_PATHS) == ["", "lastUpdatedTime", "version", "", ""] as String[]
            updateStudentMethod2.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_INDEXES) == ["-1", "-1", "1", "-1", "-1"] as String[]
            updateStudentMethod2.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def updateStudentMethod3 = repository.findPossibleMethods("updateStudent3").findFirst().get()
            def updateStudentQuery3 = updateStudentMethod3.stringValue(Query).get()

        then:
            updateStudentQuery3 == 'UPDATE `student` SET `name`=?,`last_updated_time`=? WHERE (`id` = ?)'
            updateStudentMethod3.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ['', "", ""] as String[]
            updateStudentMethod3.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['1', '-1', '0'] as String[]
            updateStudentMethod3.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PROPERTY_PATHS) == ["", "lastUpdatedTime", ""] as String[]
            updateStudentMethod3.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS) == ["", "", ""] as String[]
            updateStudentMethod3.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_INDEXES) == ["-1", "-1", "-1"] as String[]
            !updateStudentMethod3.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK).isPresent()

        when:
            def updateByStudentMethod = repository.findPossibleMethods("updateById").findFirst().get()
            def updateByStudentQuery = updateByStudentMethod.stringValue(Query).get()

        then:
            updateByStudentQuery == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            updateByStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ['', "", "", "", ""] as String[]
            updateByStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['2', '-1', '-1', '0', "1"] as String[]
            updateByStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PROPERTY_PATHS) == ["", "lastUpdatedTime", "version", "", ""] as String[]
            updateByStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS) == ["", "", "", "", ""] as String[]
            updateByStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_INDEXES) == ["-1", "-1", "1", "-1", "-1"] as String[]
            updateByStudentMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def deleteOneMethod = repository.findPossibleMethods("delete").filter({ it -> it.getArguments().size() == 1 }).findFirst().get()
            def deleteOneQuery = deleteOneMethod.stringValue(Query).get()

        then:
            deleteOneQuery == 'DELETE  FROM `student`  WHERE (`id` = ? AND `version` = ?)'
            deleteOneMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ["id", ''] as String[]
            deleteOneMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS) == ["", "version"] as String[]
            deleteOneMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def deleteStudentMethod = repository.findPossibleMethods("delete").filter({ it -> it.getArguments().size() > 1 }).findFirst().get()
            def deleteStudentQuery = deleteStudentMethod.stringValue(Query).get()

        then:
            deleteStudentQuery == 'DELETE  FROM `student`  WHERE (`id` = ? AND `version` = ? AND `name` = ?)'
            deleteStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ["", "", ""] as String[]
            deleteStudentMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['0', '1', '2'] as String[]
            deleteStudentMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def deleteByIdAndVersionAndNameMethod = repository.findPossibleMethods("deleteByIdAndVersionAndName").findFirst().get()
            def deleteByIdAndVersionAndNameQuery = deleteStudentMethod.stringValue(Query).get()

        then:
            deleteByIdAndVersionAndNameQuery == 'DELETE  FROM `student`  WHERE (`id` = ? AND `version` = ? AND `name` = ?)'
            deleteByIdAndVersionAndNameMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == ["", "", ""] as String[]
            deleteByIdAndVersionAndNameMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == ['0', '1', '2'] as String[]
            deleteByIdAndVersionAndNameMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)
    }
}