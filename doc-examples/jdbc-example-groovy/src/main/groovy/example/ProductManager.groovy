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
package example

import io.micronaut.transaction.SynchronousTransactionManager
import javax.inject.Singleton
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@Singleton
class ProductManager {

    private final Connection connection
    private final SynchronousTransactionManager<Connection> transactionManager

    ProductManager(
            Connection connection,
            SynchronousTransactionManager<Connection> transactionManager) { // <1>
        this.connection = connection
        this.transactionManager = transactionManager
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite { // <2>
            final Product product = new Product(name, manufacturer)
            connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)")
                .withCloseable { PreparedStatement ps ->
                    ps.setString(1, name)
                    ps.setLong(2, manufacturer.getId())
                    ps.execute()
                }
            return product
        }
    }

    Product find(String name) {
        return transactionManager.executeRead{ // <3>
            connection
                .prepareStatement("select * from product p where p.name = ?").withCloseable {
                    PreparedStatement ps ->
                ps.setString(1, name)
                ps.executeQuery().withCloseable { ResultSet rs ->
                    if (rs.next()) {
                        return new Product(rs.getString("name"), null)
                    }
                    return null
                }
            }
        }
    }
}
