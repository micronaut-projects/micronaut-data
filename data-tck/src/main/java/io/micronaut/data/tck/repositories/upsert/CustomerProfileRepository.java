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
package io.micronaut.data.tck.repositories.upsert;

import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfile;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CustomerProfileRepository extends CrudRepository<CustomerProfile, Long> {

    @Upsert(conflictProperties = "email")
    CustomerProfile upsert(CustomerProfile customerProfile);

    @Upsert(conflictProperties = "email")
    Mono<CustomerProfile> upsertMono(CustomerProfile profile);

    @Upsert(conflictProperties = "email")
    CompletableFuture<CustomerProfile> upsertFuture(CustomerProfile profile);

    @Upsert(conflictProperties = "email")
    void upsertNoResult(CustomerProfile customerProfile);

    @Upsert(conflictProperties = "email")
    Mono<Void> upsertMonoNoResult(CustomerProfile customerProfile);

    @Upsert(conflictProperties = "email")
    CompletableFuture<Void> upsertFutureNoResult(CustomerProfile profile);

    @Upsert(conflictProperties = "email")
    List<CustomerProfile> upsertAll(Iterable<CustomerProfile> customerProfiles);

    @Upsert(conflictProperties = "email")
    Mono<List<CustomerProfile>> upsertAllMono(Iterable<CustomerProfile> profiles);

    @Upsert(conflictProperties = "email")
    CompletableFuture<List<CustomerProfile>> upsertAllFuture(Iterable<CustomerProfile> profiles);



    @Upsert(conflictProperties = "email")
    void upsertAllNoResult(Iterable<CustomerProfile> customerProfiles);




}
