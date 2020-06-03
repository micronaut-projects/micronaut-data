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
package example;

import io.micronaut.transaction.SynchronousTransactionManager;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.sql.Connection;

@Singleton
public class ProductManager {

    private final EntityManager entityManager;
    private final SynchronousTransactionManager<Connection> transactionManager;

    public ProductManager(
            EntityManager entityManager,
            SynchronousTransactionManager<Connection> transactionManager) { // <1>
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer);
            entityManager.persist(product);
            return product;
        });
    }

    Product find(String name) {
        return transactionManager.executeRead(status -> // <3>
                entityManager.createQuery("from Product p where p.name = :name", Product.class)
                    .setParameter("name", name)
                    .getSingleResult()
        );
    }
}
