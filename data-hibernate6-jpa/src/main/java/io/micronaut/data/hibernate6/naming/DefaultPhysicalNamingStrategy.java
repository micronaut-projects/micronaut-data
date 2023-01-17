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
package io.micronaut.data.hibernate6.naming;

import io.micronaut.data.model.naming.NamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import jakarta.inject.Singleton;

/**
 * The default {@link PhysicalNamingStrategy} to use. Can be replaced with another bean that declares:
 * {@code @Replaces(DefaultPhysicalNamingStrategy.class)} and implements {@link PhysicalNamingStrategy}
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Singleton
public final class DefaultPhysicalNamingStrategy implements PhysicalNamingStrategy {
    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return getIdentifier(name);
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return getIdentifier(name);
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return getIdentifier(name);
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return getIdentifier(name);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return getIdentifier(name);
    }

    private Identifier getIdentifier(Identifier name) {
        if (name == null) {
            return null;
        }
        return new Identifier(
                NamingStrategy.DEFAULT.mappedName(name.getText()),
                name.isQuoted()
        );
    }
}
