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
package io.micronaut.transaction.hibernate5;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.support.TransactionSynchronizationAdapter;
import org.hibernate.Session;

/**
 * Simple synchronization adapter that propagates a {@code flush()} call
 * to the underlying Hibernate Session. Used in combination with JTA.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 4.2
 */
@Internal
public class FlushSynchronization extends TransactionSynchronizationAdapter {

    private final Session session;

    /**
     * Default constructor.
     * @param session The session
     */
    FlushSynchronization(@NonNull Session session) {
        this.session = session;
    }

    @Override
    public void flush() {
        SessionFactoryUtils.flush(this.session, false);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return (this == other || (other instanceof FlushSynchronization &&
                this.session == ((FlushSynchronization) other).session));
    }

    @Override
    public int hashCode() {
        return this.session.hashCode();
    }
}
