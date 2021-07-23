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
import io.micronaut.inject.annotation.NamedAnnotationMapper;

/**
 * Maps JPA's {@code JoinTable} annotation to Micronaut's.
 *
 * @author Denis Stepanov
 * @since 2.4.8
 */
public final class JoinTableAnnotationMapper extends io.micronaut.data.processor.mappers.jpa.jx.JoinTableAnnotationMapper {

    private final JoinColumnAnnotationMapper JOIN_COLUMN_ANNOTATION_MAPPER = new JoinColumnAnnotationMapper();

    /**
     * Override to replace the join column mapper.
     *
     * @return the join column mapper
     */
    @Override
    protected NamedAnnotationMapper getJoinColumnAnnotationMapper() {
        return JOIN_COLUMN_ANNOTATION_MAPPER;
    }

    @NonNull
    @Override
    public String getName() {
        return "jakarta.persistence.JoinTable";
    }

}
