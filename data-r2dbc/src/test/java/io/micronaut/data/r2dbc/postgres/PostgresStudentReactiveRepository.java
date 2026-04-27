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
package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.connection.annotation.TransactionPriority;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Student;
import io.micronaut.data.tck.repositories.StudentReactiveRepository;
import io.micronaut.transaction.annotation.Transactional;
import io.reactivex.Single;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresStudentReactiveRepository extends StudentReactiveRepository {

    @Override
    @Transactional
    // Verify non-Oracle databases ignore TransactionPriority annotations
    @TransactionPriority(TransactionPriority.Level.MEDIUM)
    <S extends Student> Single<S> save(S entity);
}
