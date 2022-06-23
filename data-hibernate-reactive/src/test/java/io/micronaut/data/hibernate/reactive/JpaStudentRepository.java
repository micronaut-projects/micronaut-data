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
package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Student;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;

@Repository
public interface JpaStudentRepository extends ReactorCrudRepository<Student, Long> {

    default Mono<Student> testUpdateOptimisticLock(Student student) {
        return saveNewStudent(student).flatMap(this::updateStudent);
    }

    default Mono<Void> testDeleteOptimisticLock(Student student) {
        return saveNewStudent(student).flatMap(this::deleteStudent);
    }

    default Mono<Void> testMergeOptimisticLock(Student student) {
        return saveNewStudent(student).flatMap(this::mergeStudent);
    }

    @Transactional
    default Mono<Student> updateStudent(Long id) {
        return findById(id).flatMap(s -> concurrentStudentUpdate(s.getId()).map(ignore -> {
            s.setName("Xyz");
            return s;
        }));
    }

    @Transactional
    default Mono<Void> deleteStudent(Long id) {
        return findById(id).flatMap(s -> concurrentStudentUpdate(s.getId()).flatMap(ignore -> delete(s))).then();
    }

    @Transactional
    default Mono<Void> mergeStudent(Long id) {
        return findById(id).flatMap(s -> concurrentStudentUpdate(s.getId()).flatMap(ignore -> {
            s.setName("Xyz");
            return update(s);
        })).then();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    default Mono<Student> concurrentStudentUpdate(Long id) {
        return findById(id).map(s -> {
            s.setName("Abc");
            return s;
        });
    }

    @Transactional
    default Mono<Long> saveNewStudent(Student student) {
        return save(student).map(Student::getId);
    }

    Mono<Void> updateStudentName(@Id Long id, @Version Long version, String name);

    Mono<Void> updateStudentNameNoVersion(@Id Long id, String name);

    Mono<Void> updateById(@Id Long id, @Version Long version, String name);

    Mono<Void> delete(@Id Long id, @Version Long version, String name);

    Mono<Void> delete(@Id Long id, @Version Long version);

    Mono<Void> updateStudentName(@Id Long id, String name);

    Mono<Void> updateById(@Id Long id, String name);

}
