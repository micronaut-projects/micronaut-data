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


import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.*

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

        then:
            getQuery(updateByIdAndVersionMethod) == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            getParameterBindingIndexes(updateByIdAndVersionMethod) == ['2', '-1', '-1', '0', '1']
            getParameterBindingPaths(updateByIdAndVersionMethod) == ['', '', '', '', '']
            getParameterPropertyPaths(updateByIdAndVersionMethod) == ["name", "lastUpdatedTime", "version", "id", "version"]
            getParameterAutoPopulatedProperties(updateByIdAndVersionMethod) == ["", "lastUpdatedTime", "version", "", "version"]
            getParameterRequiresPreviousPopulatedValueProperties(updateByIdAndVersionMethod) == ["", "", "", "", ""]
            updateByIdAndVersionMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK).get()

            def updateOneMethod = repository.findPossibleMethods("update").filter({ it -> it.getArguments().size() == 1 }).findFirst().get()

        then:
            getQuery(updateOneMethod) == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            getParameterBindingIndexes(updateOneMethod) == ['-1', '-1', '-1', '-1', '-1']
            getParameterBindingPaths(updateOneMethod) == ['', '', '', '', '']
            getParameterPropertyPaths(updateOneMethod) == ["name", "lastUpdatedTime", "version", "id", "version"]
            getParameterAutoPopulatedProperties(updateOneMethod) == ["", "lastUpdatedTime", "version", "", "version"]
            getParameterRequiresPreviousPopulatedValueProperties(updateOneMethod) == ["", "", "", "", "version"]
            updateOneMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def updateStudentMethod1 = repository.findPossibleMethods("updateStudent1").findFirst().get()

        then:
            getQuery(updateStudentMethod1) == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            getParameterBindingIndexes(updateStudentMethod1) == ['2', '-1', '-1', '0', "1"]
            getParameterBindingPaths(updateStudentMethod1) == ['', '', '', '', '']
            getParameterPropertyPaths(updateStudentMethod1) == ["name", "lastUpdatedTime", "version", "id", "version"]
            getParameterAutoPopulatedProperties(updateStudentMethod1) == ["", "lastUpdatedTime", "version", "", "version"]
            getParameterRequiresPreviousPopulatedValueProperties(updateStudentMethod1) == ["", "", "", "", ""]
            updateStudentMethod1.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def updateStudentMethod2 = repository.findPossibleMethods("updateStudent2").findFirst().get()

        then:
            getQuery(updateStudentMethod2) == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            getParameterBindingIndexes(updateStudentMethod2) == ['2', '-1', '-1', '0', "1"]
            getParameterBindingPaths(updateStudentMethod2) == ['', '', '', '', '']
            getParameterPropertyPaths(updateStudentMethod2) == ["name", "lastUpdatedTime", "version", "id", "version"]
            getParameterAutoPopulatedProperties(updateStudentMethod2) == ["", "lastUpdatedTime", "version", "", "version"]
            getParameterRequiresPreviousPopulatedValueProperties(updateStudentMethod2) == ["", "", "", "", ""]
            updateStudentMethod2.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def updateStudentMethod3 = repository.findPossibleMethods("updateStudent3").findFirst().get()

        then:
            getQuery(updateStudentMethod3) == 'UPDATE `student` SET `name`=?,`last_updated_time`=? WHERE (`id` = ?)'
            getParameterBindingIndexes(updateStudentMethod3) == ['1', '-1', '0']
            getParameterBindingPaths(updateStudentMethod3) == ['', '', '']
            getParameterPropertyPaths(updateStudentMethod3) == ["name", "lastUpdatedTime", "id"]
            getParameterAutoPopulatedProperties(updateStudentMethod3) == ["", "lastUpdatedTime", ""]
            getParameterRequiresPreviousPopulatedValueProperties(updateStudentMethod3) == ["", "", ""]
            !updateStudentMethod3.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK).isPresent()

        when:
            def updateByStudentMethod = repository.findPossibleMethods("updateById").findFirst().get()

        then:
            getQuery(updateByStudentMethod) == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
            getParameterBindingIndexes(updateByStudentMethod) == ['2', '-1', '-1', '0', "1"]
            getParameterBindingPaths(updateByStudentMethod) == ['', "", "", "", ""]
            getParameterPropertyPaths(updateByStudentMethod) == ["name", "lastUpdatedTime", "version", "id", "version"]
            getParameterAutoPopulatedProperties(updateByStudentMethod) == ["", "lastUpdatedTime", "version", "", "version"]
            getParameterRequiresPreviousPopulatedValueProperties(updateByStudentMethod) == ["", "", "", "", ""]
            updateByStudentMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def deleteOneMethod = repository.findPossibleMethods("delete").filter({ it -> it.getArguments().size() == 1 }).findFirst().get()

        then:
            getQuery(deleteOneMethod) == 'DELETE  FROM `student`  WHERE (`id` = ? AND `version` = ?)'
            getParameterBindingIndexes(deleteOneMethod) == ["-1", "-1"]
            getParameterBindingPaths(deleteOneMethod) == ['', ""]
            getParameterPropertyPaths(deleteOneMethod) == [ "id", "version"]
            deleteOneMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def deleteStudentMethod = repository.findPossibleMethods("delete").filter({ it -> it.getArguments().size() > 1 }).findFirst().get()

        then:
            getQuery(deleteStudentMethod) == 'DELETE  FROM `student`  WHERE (`id` = ? AND `version` = ? AND `name` = ?)'
            getParameterBindingIndexes(deleteStudentMethod) == ['0', '1', '2']
            getParameterBindingPaths(deleteStudentMethod) == ['', "", ""]
            getParameterPropertyPaths(deleteStudentMethod) == [ "id", "version", "name"]
            deleteStudentMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)

        when:
            def deleteByIdAndVersionAndNameMethod = repository.findPossibleMethods("deleteByIdAndVersionAndName").findFirst().get()

        then:
            getQuery(deleteByIdAndVersionAndNameMethod) == 'DELETE  FROM `student`  WHERE (`id` = ? AND `version` = ? AND `name` = ?)'
            getParameterBindingIndexes(deleteByIdAndVersionAndNameMethod) == ['0', '1', '2']
            getParameterBindingPaths(deleteByIdAndVersionAndNameMethod) == ['', "", ""]
            getParameterPropertyPaths(deleteByIdAndVersionAndNameMethod) == [ "id", "version", "name"]
            deleteByIdAndVersionAndNameMethod.booleanValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK)
    }
}