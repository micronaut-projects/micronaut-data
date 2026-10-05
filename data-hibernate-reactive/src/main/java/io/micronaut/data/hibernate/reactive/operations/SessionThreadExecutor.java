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
package io.micronaut.data.hibernate.reactive.operations;

import io.micronaut.core.annotation.Internal;
import io.vertx.core.Context;

import java.util.concurrent.Executor;

/**
 * Runs tasks on the thread a Hibernate Reactive session was opened on. Hibernate Reactive rejects using a session
 * from another thread, which happens when a reactive pipeline continues on another thread, for example after a
 * reactive HTTP client call or when a subscriber completes a streamed result.
 *
 * @param thread  The thread the session was opened on
 * @param context The Vert.x context of that thread
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record SessionThreadExecutor(Thread thread, Context context) implements Executor {

    @Override
    public void execute(Runnable task) {
        if (Thread.currentThread() == thread) {
            task.run();
        } else {
            context.runOnContext(ignore -> task.run());
        }
    }
}
