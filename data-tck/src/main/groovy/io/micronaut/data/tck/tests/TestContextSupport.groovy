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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext

import java.net.ConnectException

final class TestContextSupport {

    private TestContextSupport() {
    }

    static ApplicationContext runWithRetry(Map<String, ?> properties) {
        return runWithRetry(ApplicationContext, properties)
    }

    static <T> T runWithRetry(Class<T> applicationType, Map<String, ?> properties, String... environments) {
        Throwable lastFailure = null
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return ApplicationContext.run(applicationType, properties, environments)
            } catch (Throwable e) {
                lastFailure = e
                if (!causedByConnectionRefused(e) || attempt == 3) {
                    throw e
                }
                Thread.sleep(1000L * attempt)
            }
        }
        throw lastFailure
    }

    private static boolean causedByConnectionRefused(Throwable e) {
        Throwable current = e
        while (current != null) {
            if (current instanceof ConnectException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
