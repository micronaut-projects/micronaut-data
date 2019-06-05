/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.backend.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.backend.Datastore;

/**
 * A {@link Datastore} capable of supporting asynchronous access.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface AsyncCapableDatastore extends Datastore {
    /**
     * @return The async datastore operations.
     */
    @NonNull
    AsyncOperations async();
}
