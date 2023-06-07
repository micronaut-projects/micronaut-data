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
package io.micronaut.transaction.hibernate6;

import io.micronaut.core.annotation.TypeHint;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * The class name should be corrected in SQL module and this file should be deleted.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 * @deprecated Replaced by {@link io.micronaut.transaction.hibernate.MicronautSessionContext}
 */
// TypeHint doesn't work for deprecated, this should be removed before the release anyway
//@Deprecated(since = "4", forRemoval = true)
@TypeHint(MicronautSessionContext.class)
public final class MicronautSessionContext extends io.micronaut.transaction.hibernate.MicronautSessionContext {

    /**
     * Create a new SpringSessionContext for the given Hibernate SessionFactory.
     *
     * @param sessionFactory the SessionFactory to provide current Sessions for
     */
    public MicronautSessionContext(SessionFactoryImplementor sessionFactory) {
        super(sessionFactory);
    }
}
