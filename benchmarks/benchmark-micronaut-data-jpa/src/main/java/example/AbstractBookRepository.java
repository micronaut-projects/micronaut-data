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

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import jakarta.persistence.EntityManager;
import java.util.List;

@Repository
public abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final EntityManager entityManager;

    public AbstractBookRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Book> findByTitle(String title) {
        return entityManager.createQuery("FROM Book AS book WHERE book.title = :title", Book.class)
                    .setParameter("title", title)
                    .getResultList();
    }
}
