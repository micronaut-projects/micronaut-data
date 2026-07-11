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
package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.IndexedOrder;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;

/**
 * Repository for {@link IndexedOrder}, used to regression-test AND/OR filters and null-handling
 * behavior on indexed fields following the nitrite 4.3.3/4.4.x planner and comparator fixes.
 */
@NitriteRepository
public interface IndexedOrderRepository extends PageableRepository<IndexedOrder, Long> {

    List<IndexedOrder> findByStatusAndAmountBetween(String status, Long from, Long to);

    List<IndexedOrder> findByStatusAndAmountGreaterThan(String status, Long amount);

    List<IndexedOrder> findByStatusOrAmount(String status, Long amount);

    List<IndexedOrder> findByAmountLessThan(Long amount);

    // Raw JSON literal `150` (parsed as a non-Long numeric type) filtered against the
    // indexed Long `amount` field, to regression-test cross-type numeric filtering on indexes.
    @Query("{\"amount\": {\"$eq\": 150}}")
    List<IndexedOrder> findByAmountLiteral150();
}
