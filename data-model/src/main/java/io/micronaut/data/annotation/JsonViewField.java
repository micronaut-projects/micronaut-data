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
package io.micronaut.data.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field level options for a property of a {@link JsonView} or {@link JsonSubView}.
 *
 * <pre>
 * {@code
 * @JsonView(entity = Customer.class)
 * public record CustomerView(
 *     @Id Long id,
 *     @JsonViewField(updatable = false) String email,
 *     @JsonViewField(etagCheck = false) String lastSeen
 * ) {}
 * }
 * </pre>
 *
 * <p>Currently supported only by Oracle Database 23ai and later.</p>
 *
 * @author graemerocher
 * @since 5.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Documented
@Experimental
public @interface JsonViewField {

    /**
     * Whether the field can be updated through the view. When {@code false} the field is generated
     * with the {@code NOUPDATE} annotation and cannot be changed through the view.
     *
     * @return true if the field is updatable (default true)
     */
    boolean updatable() default true;

    /**
     * Whether the field contributes to the ETAG of the document. When {@code false} the field is generated
     * with the {@code NOCHECK} annotation, so concurrent changes to it do not invalidate the document ETAG.
     *
     * @return true if the field is part of the ETAG check (default true)
     */
    boolean etagCheck() default true;
}
