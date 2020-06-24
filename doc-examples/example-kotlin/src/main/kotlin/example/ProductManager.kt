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
import javax.persistence.EntityManager

@Singleton
class ProductManager(
        private val entityManager: EntityManager,
        private val transactionManager: SynchronousTransactionManager<Connection>) // <1>
{

    fun save(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite { // <2>
            val product = Product(0, name, manufacturer)
            entityManager.persist(product)
            product
        }
    }

    fun find(name: String): Product {
        return transactionManager.executeRead {  // <3>
            entityManager.createQuery("from Product p where p.name = :name", Product::class.java)
                    .setParameter("name", name)
                    .singleResult
        }
    }
}
