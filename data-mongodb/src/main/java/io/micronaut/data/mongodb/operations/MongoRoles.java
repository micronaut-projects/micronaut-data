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

/**
 * Mongo parameter roles contansts.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Internal
public interface MongoRoles {

    String FILTER_ROLE = "filter";
    String PIPELINE_ROLE = "pipeline";
    String UPDATE_ROLE = "update";
    String FIND_OPTIONS_ROLE = "findOptions";
    String AGGREGATE_OPTIONS_ROLE = "aggregateOptions";
    String UPDATE_OPTIONS_ROLE = "updateOptions";
    String DELETE_OPTIONS_ROLE = "deleteOptions";

}
