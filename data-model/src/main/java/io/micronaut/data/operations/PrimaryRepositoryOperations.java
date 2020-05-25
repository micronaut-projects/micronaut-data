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
package io.micronaut.data.operations;

/**
 * In the case of having two operations active (for example when using JPA and JDBC at the same time)
 * this interface is used as a marker to decide on the primary operations to lookup.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PrimaryRepositoryOperations extends RepositoryOperations {
}
