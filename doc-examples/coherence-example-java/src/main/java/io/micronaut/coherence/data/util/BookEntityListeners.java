/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.coherence.data.util;

import io.micronaut.coherence.data.model.Book;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.event.PostPersist;
import io.micronaut.data.annotation.event.PostRemove;
import io.micronaut.data.annotation.event.PostUpdate;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreRemove;
import io.micronaut.data.annotation.event.PreUpdate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Requires(notEnv = {"evict-persist", "evict-delete", "evict-update"})
@SuppressWarnings("checkstyle:DesignForExtension")
public class BookEntityListeners {

    @Inject
    EventRecorder<Book> eventRecorder;

    // ----- listeners ------------------------------------------------------

    @PrePersist
    public void prePersist(Book book) {
        eventRecorder.record(EventType.PRE_PERSIST, book);
    }

    @PostPersist
    public void postPersist(Book book) {
        eventRecorder.record(EventType.POST_PERSIST, book);
    }

    @PreUpdate
    public void preUpdate(Book book) {
        eventRecorder.record(EventType.PRE_UPDATE, book);
    }

    @PostUpdate
    public void postUpdate(Book book) {
        eventRecorder.record(EventType.POST_UPDATE, book);
    }

    @PreRemove
    public void preRemove(Book book) {
        eventRecorder.record(EventType.PRE_REMOVE, book);
    }

    @PostRemove
    public void postRemove(Book book) {
        eventRecorder.record(EventType.POST_REMOVE, book);
    }
}
