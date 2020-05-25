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
package io.micronaut.data.jdbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import java.sql.Connection

@MicronautTest
class PostgresSequenceDefaultSpec extends Specification implements TestPropertyProvider {

    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")
    @Inject Connection connection
    @Inject UserRepository userRepository

    void setup() {
        connection.prepareStatement('''
create sequence "user_seq" increment by 1;
create table "user_"
(
  "id"                 bigint default nextval('user_seq'::regclass)    not null,
  "username"           varchar(255)                                    ,
  "email"              varchar(255)                                    ,
  "password"           varchar(4000)                                   ,
  "hash"               varchar(32)
);
''').withCloseable { it.executeUpdate( )}
    }

    void "test sequence generation with default nextval"() {
        when:
        User user = userRepository.save("Fred")
        User user2 = userRepository.save("Bob")

        then:
        user.id
        user.id > 0
        user.id != user2.id
        user.id == userRepository.findById(user.id).id
    }

    @Override
    Map<String, String> getProperties() {
        postgres.start()
        return [
                "datasources.default.url":postgres.getJdbcUrl(),
                "datasources.default.username":postgres.getUsername(),
                "datasources.default.password":postgres.getPassword(),
                "datasources.default.dialect": Dialect.POSTGRES
        ]
    }


}
