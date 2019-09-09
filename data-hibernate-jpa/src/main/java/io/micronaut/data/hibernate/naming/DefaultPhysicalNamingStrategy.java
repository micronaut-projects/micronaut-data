package io.micronaut.data.hibernate.naming;

import io.micronaut.data.model.naming.NamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import javax.inject.Singleton;

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
