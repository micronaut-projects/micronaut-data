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
package io.micronaut.data.search.document.runtime;

import io.micronaut.data.exceptions.DataAccessException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The failure of a bulk index request that the store applied in part: a bulk request is not atomic,
 * so the documents the store accepted stay written. {@link #getIds()} tells them apart; the
 * repository operations still store the id of each written entity and fire its post-persist event
 * before the exception reaches the caller. When a rejected document of a versioned entity conflicted
 * with the stored version, the caller gets an {@link io.micronaut.data.exceptions.OptimisticLockException}
 * whose cause is this exception.
 *
 * @since 5.3.0
 */
public final class BulkIndexException extends DataAccessException {

    private final transient List<@Nullable String> ids;

    /**
     * @param message The message
     * @param ids The id of each document of the request in request order, {@code null} for one that was not written
     */
    public BulkIndexException(String message, List<@Nullable String> ids) {
        super(message);
        this.ids = Collections.unmodifiableList(new ArrayList<>(ids));
    }

    /**
     * @param message The message
     * @param ids The id of each document of the request in request order, {@code null} for one that was not written
     * @param cause The cause
     */
    public BulkIndexException(String message, List<@Nullable String> ids, Throwable cause) {
        super(message, cause);
        this.ids = Collections.unmodifiableList(new ArrayList<>(ids));
    }

    /**
     * @return The id of each document of the request in request order, {@code null} for one that was not written
     */
    public List<@Nullable String> getIds() {
        return ids;
    }
}
