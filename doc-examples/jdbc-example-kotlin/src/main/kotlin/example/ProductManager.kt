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
import java.sql.Connection
import javax.inject.Singleton

@Singleton
class ProductManager(
        private val connection: Connection,
        private val transactionManager: SynchronousTransactionManager<Connection>) // <1>
{

    fun save(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite { // <2>
            val product = Product(0, name, manufacturer)
            connection.prepareStatement("insert into product (name, manufacturer_id) values (?, ?)").use { ps ->
                ps.setString(1, name)
                ps.setLong(2, manufacturer.id!!)
                ps.execute()
            }
            product
        }
    }

    fun find(name: String): Product? {
        return transactionManager.executeRead { // <3>
            connection
                    .prepareStatement("select * from product p where p.name = ?").use { ps ->
                        ps.setString(1, name)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@executeRead Product(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        null)
                            }
                            return@executeRead null
                        }
                    }
        }
    }
}
