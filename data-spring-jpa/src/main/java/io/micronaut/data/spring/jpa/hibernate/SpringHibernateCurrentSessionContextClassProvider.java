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
package io.micronaut.data.spring.jpa.hibernate;

import io.micronaut.configuration.hibernate.jpa.HibernateCurrentSessionContextClassProvider;
import io.micronaut.core.annotation.TypeHint;
import jakarta.inject.Singleton;
import org.hibernate.context.spi.CurrentSessionContext;
import org.springframework.orm.hibernate5.SpringSessionContext;

/**
 * Spring integration implementation of {@link HibernateCurrentSessionContextClassProvider}.
 *
 * @author Denis Stepanov
 * @since 3.3.2
 */
@TypeHint(SpringSessionContext.class)
@Singleton
public class SpringHibernateCurrentSessionContextClassProvider implements HibernateCurrentSessionContextClassProvider {

    /**
     * @return SpringSessionContext
     */
    @Override
    public Class<? extends CurrentSessionContext> get() {
        return SpringSessionContext.class;
    }

}
