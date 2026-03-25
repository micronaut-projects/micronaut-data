/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.document.mongo.MongoAnnotations;

/**
 * Mongo parameter roles contansts.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Internal
public interface MongoRoles {

    String FILTER_ROLE = MongoAnnotations.FILTER_ROLE;
    String PIPELINE_ROLE = MongoAnnotations.PIPELINE_ROLE;
    String UPDATE_ROLE = MongoAnnotations.UPDATE_ROLE;
    String FIND_OPTIONS_ROLE = MongoAnnotations.FIND_OPTIONS_ROLE;
    String AGGREGATE_OPTIONS_ROLE = MongoAnnotations.AGGREGATE_OPTIONS_ROLE;
    String UPDATE_OPTIONS_ROLE = MongoAnnotations.UPDATE_OPTIONS_ROLE;
    String DELETE_OPTIONS_ROLE = MongoAnnotations.DELETE_OPTIONS_ROLE;

}
