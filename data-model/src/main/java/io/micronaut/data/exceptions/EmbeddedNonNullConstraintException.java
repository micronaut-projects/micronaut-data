/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.exceptions;


public class EmbeddedNonNullConstraintException extends DataAccessException {
    private final String path;

    public EmbeddedNonNullConstraintException(String path, String entityName) {
        super("Null value read for non-null constructor argument [" + path + "] of type: " + entityName);
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
