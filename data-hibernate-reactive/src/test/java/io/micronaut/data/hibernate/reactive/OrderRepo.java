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

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Order;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public interface OrderRepo extends ReactorCrudRepository<Order, Long> {
    Mono<BigDecimal> findSumTotalAmountByCustomer(String customer);

    Mono<Double> findSumWeightByCustomer(String customer);

    Mono<Integer> findSumUnitsByCustomer(String customer);

    Mono<Long> findSumTaxByCustomer(String customer);
}
