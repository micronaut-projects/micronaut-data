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
package example

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

import javax.validation.constraints.NotNull

@JdbcRepository(dialect = Dialect.H2)
interface UserRepository extends CrudRepository<User, Long> { // <1>

    @Override
    @Query("UPDATE user SET enabled = false WHERE id = :id") // <2>
    void deleteById(@NonNull @NotNull Long id)

    @Query("SELECT * FROM user WHERE enabled = false") // <3>
    List<User> findDisabled()
}
