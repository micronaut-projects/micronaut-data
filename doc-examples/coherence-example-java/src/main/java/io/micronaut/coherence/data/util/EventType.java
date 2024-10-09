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

public enum EventType {
    /**
     * @see io.micronaut.data.annotation.event.PrePersist
     */
    PRE_PERSIST,

    /**
     * @see io.micronaut.data.annotation.event.PostPersist
     */
    POST_PERSIST,

    /**
     * @see io.micronaut.data.annotation.event.PreUpdate
     */
    PRE_UPDATE,

    /**
     * @see io.micronaut.data.annotation.event.PostUpdate
     */
    POST_UPDATE,

    /**
     * @see io.micronaut.data.annotation.event.PreRemove
     */
    PRE_REMOVE,

    /**
     * @see io.micronaut.data.annotation.event.PostRemove
     */
    POST_REMOVE
}
