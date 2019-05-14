/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.spring;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.Sort;

/**
 * Supports representing a Spring Pageable as a Micronaut {@link Pageable}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class PageableDelegate implements Pageable {

    private final org.springframework.data.domain.Pageable target;

    PageableDelegate(org.springframework.data.domain.Pageable target) {
        this.target = target;
    }

    @Override
    public int getNumber() {
        return target.getPageNumber();
    }

    @Override
    public int getSize() {
        return target.getPageSize();
    }

    @Override
    public long getOffset() {
        return target.getOffset();
    }

    @NonNull
    @Override
    public Sort getSort() {
        return new SortDelegate(target.getSort());
    }
}
