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
package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Student;

import java.util.Optional;

public interface StudentRepository extends CrudRepository<Student, Long> {

    void updateByIdAndVersion(Long id, Long version, String name);

    void updateById(@Id Long id, @Parameter("name") String name);

    void deleteByIdAndVersionAndName(@Id Long id, @Version Long version, String name);

    void deleteByIdAndVersion(@Id Long id, @Version Long version);

    @Join(value = "books", type = Join.Type.FETCH)
    Optional<Student> findByName(String name);
}
