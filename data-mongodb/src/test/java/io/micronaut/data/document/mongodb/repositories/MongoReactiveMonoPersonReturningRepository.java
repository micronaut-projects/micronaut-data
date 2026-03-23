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
package io.micronaut.data.document.mongodb.repositories;

import com.mongodb.client.model.ReturnDocument;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.mongodb.annotation.MongoUpdateQuery;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@MongoRepository
public interface MongoReactiveMonoPersonReturningRepository extends ReactorCrudRepository<Person, String> {

    @MongoUpdateQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}")
    @MongoUpdateOptions(returnDocument = ReturnDocument.AFTER)
    Mono<Person> updateCustomReturning(String id, String newName);
}
