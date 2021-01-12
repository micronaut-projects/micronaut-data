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
package io.micronaut.data.autopopulated;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

/**
 * The interface representing an auto-populated property provider.
 *
 * @author Denis Stepanov
 * @since 2.3.0
 */
@Indexed(EntityAutoPopulatedPropertyProvider.class)
public interface EntityAutoPopulatedPropertyProvider extends Ordered {

    /**
     * Auto-populating the property.
     *
     * @param property the property to auto-populate
     * @param previousValue the previous value
     * @return new value
     */
    Object autoPopulate(RuntimePersistentProperty<?> property, @Nullable Object previousValue);

    /**
     * Does supports auto-populating the property.
     *
     * @param property the property to check
     * @return true if provider supports the property
     */
    boolean supports(RuntimePersistentProperty<?> property);
}
