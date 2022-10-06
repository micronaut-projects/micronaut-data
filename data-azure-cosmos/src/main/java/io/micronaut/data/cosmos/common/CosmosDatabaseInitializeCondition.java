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
package io.micronaut.data.cosmos.common;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.cosmos.config.StorageUpdatePolicy;

/**
 * Condition that checks whether Cosmos Db needs to be initialized. It depends on {@link StorageUpdatePolicy}
 * value from the configuration and initialization is needed when value is different from {@link StorageUpdatePolicy#NONE}.
 */
public class CosmosDatabaseInitializeCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        CosmosDatabaseConfiguration configuration = context.getBean(CosmosDatabaseConfiguration.class);
        StorageUpdatePolicy storageUpdatePolicy = configuration.getUpdatePolicy();
        return StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.equals(storageUpdatePolicy) || StorageUpdatePolicy.UPDATE.equals(storageUpdatePolicy);
    }
}
