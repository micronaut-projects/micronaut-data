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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes an update that has to read a document before it can write it.
 *
 * <p>Nitrite has no arithmetic update operator, so an operation such as {@code $inc} is carried
 * out by reading the stored value, computing the new one and writing it back. Nitrite guards each
 * individual call with its own per-collection lock, but that lock is released between the read and
 * the write, so two threads can read the same value and one update is lost. Holding a lock across
 * the whole sequence closes that window.
 *
 * <p>The database is embedded and a database file is owned by a single process, so a lock held
 * within this JVM is sufficient. Locks are keyed by collection name, so updates to different
 * collections still run concurrently.
 *
 * @since 5.2.0
 */
@Internal
public final class CollectionUpdateLock {

    private static final ConcurrentMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private CollectionUpdateLock() {
    }

    /**
     * Runs a read-modify-write sequence while holding the lock for the given collection.
     *
     * @param collection the collection name
     * @param action     the sequence to run
     * @param <T>        the result type
     * @return the result of the sequence
     */
    public static <T> T withLock(String collection, Supplier<T> action) {
        ReentrantLock lock = LOCKS.computeIfAbsent(collection, name -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
