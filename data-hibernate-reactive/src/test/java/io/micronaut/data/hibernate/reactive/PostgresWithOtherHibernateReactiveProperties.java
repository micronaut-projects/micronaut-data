package io.micronaut.data.hibernate.reactive;

import java.util.HashMap;
import java.util.Map;

public interface PostgresWithOtherHibernateReactiveProperties extends PostgresHibernateReactiveProperties {

    @Override
    default Map<String, String> getProperties() {
        Map<String, String> map = new HashMap<>(PostgresHibernateReactiveProperties.super.getProperties());
        map.putAll(Map.of(
                "jpa.other.properties.hibernate.hbm2ddl.auto", "create-drop",
                "jpa.other.reactive", "true",
                "jpa.other.properties.hibernate.connection.db-type", "postgres"
        ));
        return map;
    }
}
