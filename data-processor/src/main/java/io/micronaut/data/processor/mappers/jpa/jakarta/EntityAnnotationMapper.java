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
package io.micronaut.data.processor.mappers.jpa.jakarta;

import io.micronaut.core.annotation.NonNull;

/**
 * Translates the {@code jakarta.persistence} annotation model into the generic model understood by Micronaut Data.
 *
 * @author Denis Stepanov
 * @since 2.4.8
 */
public final class EntityAnnotationMapper extends io.micronaut.data.processor.mappers.jpa.jx.EntityAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "jakarta.persistence.Entity";
    }

}
