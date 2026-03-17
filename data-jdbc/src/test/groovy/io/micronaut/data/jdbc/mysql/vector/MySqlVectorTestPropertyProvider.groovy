package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.data.jdbc.mysql.MySQLTestPropertyProvider

trait MySqlVectorTestPropertyProvider implements MySQLTestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return (super.getProperties() + [
                'test-resources.containers.mysql.image-name': 'container-registry.oracle.com/mysql/community-server:9.6.0'
        ]) as Map<String, String>
    }
}
