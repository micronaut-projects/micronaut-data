/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;

import java.lang.annotation.Annotation;

/**
 * The interface allows automatically populating new values, potentially based on the previous value for {@link io.micronaut.data.annotation.AutoPopulated} properties.
 *
 * <p>NOTE: Regarded as experimental and internal at this stage.</p>
 *
 * @param <T> the populated annotation type
 *
 * @author Denis Stepanov
 * @author graemerocher
 * @since 2.3.0
 * @see io.micronaut.data.annotation.AutoPopulated
 */
@Indexed(PropertyAutoPopulator.class)
@Internal
@Experimental
@FunctionalInterface
public interface PropertyAutoPopulator<T extends Annotation> extends Ordered {

    /**
     * Auto-populating the property.
     *
     * @param property the property to auto-populate
     * @param previousValue the previous value, if any
     * @return new value
     */
    @NonNull Object populate(RuntimePersistentProperty<?> property, @Nullable Object previousValue);
}
