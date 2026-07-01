package io.micronaut.data.r2dbc.postgres.vector

import io.micronaut.data.r2dbc.postgres.PostgresTestPropertyProvider

trait PostgresVectorTestPropertyProvider implements PostgresTestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return (super.getProperties() + [
                'test-resources.containers.postgres.image-name': 'pgvector/pgvector:pg16'
        ]) as Map<String, String>
    }
}
