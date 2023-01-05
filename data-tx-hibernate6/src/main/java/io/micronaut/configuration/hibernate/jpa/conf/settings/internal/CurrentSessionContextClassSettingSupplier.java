/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.configuration.hibernate.jpa.conf.settings.internal;

import io.micronaut.configuration.hibernate.jpa.HibernateCurrentSessionContextClassProvider;
import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.configuration.hibernate.jpa.conf.settings.SettingsSupplier;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.hibernate6.MicronautSessionContext;
import org.hibernate.cfg.AvailableSettings;

import java.util.Collections;
import java.util.Map;

/**
 * Current settings settings supplier.
 *
 * @author Denis Stepanov
 * @since 4.5.0
 */
@Internal
@Prototype
final class CurrentSessionContextClassSettingSupplier implements SettingsSupplier {

    private final BeanProvider<HibernateCurrentSessionContextClassProvider> provider;

    CurrentSessionContextClassSettingSupplier(BeanProvider<HibernateCurrentSessionContextClassProvider> provider) {
        this.provider = provider;
    }

    @Override
    public Map<String, Object> supply(JpaConfiguration jpaConfiguration) {
        String currentSession = provider.find(null)
                .map(provider -> provider.get().getName())
                .orElseGet(MicronautSessionContext.class::getName);
        return Collections.singletonMap(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, currentSession);
    }
}
