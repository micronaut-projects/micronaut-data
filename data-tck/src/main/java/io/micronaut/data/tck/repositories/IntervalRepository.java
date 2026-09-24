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
package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.jdbc.entities.IntervalEntity;

import java.time.Duration;
import java.time.Period;
import java.util.List;

public interface IntervalRepository extends PageableRepository<IntervalEntity, Integer> {

    @Query("INSERT INTO interval_entity(duration, period) VALUES (:dur, :per)")
    void saveCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period);

    @Query("SELECT * FROM interval_entity WHERE duration = :dur AND period = :per ORDER BY id ASC")
    List<IntervalEntity> findCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period);

    @Query("UPDATE interval_entity SET duration = :dur, period = :per WHERE id = :id")
    void updateCustom(Integer id, @Parameter("dur") Duration duration, @Parameter("per") Period period);
}
