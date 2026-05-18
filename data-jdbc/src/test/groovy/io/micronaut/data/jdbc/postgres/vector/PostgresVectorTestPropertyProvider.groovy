package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider

trait PostgresVectorTestPropertyProvider implements PostgresTestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return (super.getProperties() + [
                'test-resources.containers.postgres.image-name': 'pgvector/pgvector:pg16'
        ]) as Map<String, String>
    }
}
