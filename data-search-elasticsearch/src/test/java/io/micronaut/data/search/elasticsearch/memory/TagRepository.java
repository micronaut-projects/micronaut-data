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
package io.micronaut.data.search.elasticsearch.memory;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;

import java.util.UUID;

/**
 * A repository of entities with generated UUID ids.
 */
@ElasticsearchRepository
public interface TagRepository extends CrudRepository<Tag, UUID> {
}
