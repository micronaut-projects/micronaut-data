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
package io.micronaut.transaction.support;

/**
 * Generic interface to be implemented by resource holders.
 * Allows transaction infrastructure to introspect
 * and reset the holder when necessary.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 2.5.5
 * @see ResourceHolderSupport
 */
public interface ResourceHolder {

    /**
     * Reset the transactional state of this holder.
     */
    void reset();

    /**
     * Notify this holder that it has been unbound from transaction synchronization.
     */
    void unbound();

    /**
     * Determine whether this holder is considered as 'void',
     * i.e. as a leftover from a previous thread.
     * @return If the resource is void
     */
    boolean isVoid();

}

