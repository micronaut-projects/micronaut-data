package io.micronaut.data.r2dbc.mysql.vector

import io.micronaut.data.r2dbc.mysql.MySqlTestPropertyProvider

trait MySqlVectorTestPropertyProvider implements MySqlTestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return (super.getProperties() + [
                'test-resources.containers.mysql.image-name': 'container-registry.oracle.com/mysql/community-server:9.6.0'
        ]) as Map<String, String>
    }
}
