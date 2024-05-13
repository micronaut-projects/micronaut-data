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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.config.DataSettings;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
public class LoggingSqlExecutionObserver implements SqlExecutionObserver {

    protected static final Logger QUERY_LOG = DataSettings.QUERY_LOG;

    @Override
    public void query(String query) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
    }

    @Override
    public void parameter(int index, Object value, DataType dataType) {
        if (QUERY_LOG.isTraceEnabled()) {
            QUERY_LOG.trace("Binding parameter at position {} to value {} with data type: {}", index, value, dataType);
        }
    }
}
