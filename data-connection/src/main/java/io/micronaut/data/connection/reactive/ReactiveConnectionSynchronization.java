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
package io.micronaut.data.connection.reactive;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Interface for the reactive connection synchronization callbacks.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
public interface ReactiveConnectionSynchronization {

    /**
     * Invoked when the connection operation execution is completed.
     *
     * @return The publisher
     */
    default Publisher<Void> onComplete() {
        return Mono.empty();
    }

    /**
     * Invoked when the connection operation execution throws an exception.
     *
     * @return The publisher
     */
    default Publisher<Void> onError(Throwable throwable) {
        return Mono.empty();
    }

    /**
     * Invoked when the connection operation is canceled.
     *
     * @return The publisher
     */
    default Publisher<Void> onCancel() {
        return Mono.empty();
    }

    /**
     * Invoked before the connection is closed.
     * NOTE: this will be executed only when the physical connection is closed. Ordinary connection can be reused for multiple connection operations.
     *
     * @return The publisher
     */
    default Publisher<Void> onClose() {
        return Mono.empty();
    }

    /**
     * Invoked after the connection is closed.
     * NOTE: this will be executed only when the physical connection is closed. Ordinary connection can be reused for multiple connection operations.
     *
     * @return The publisher
     */
    default Publisher<Void> afterClose() {
        return Mono.empty();
    }

}

