/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.h2.external;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.h2.H2BookRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "H2ExternalTransactionSpec")
@Singleton
public class ExternalTxBookService {

    private final H2BookRepository bookRepository;

    public ExternalTxBookService(H2BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional
    public void saveRequired(String title) {
        bookRepository.save(book(title));
    }

    @Transactional
    public void saveRequiredAndFail(String title) {
        bookRepository.save(book(title));
        throw new IllegalStateException("Failure after " + title);
    }

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public void saveRequiresNew(String title) {
        bookRepository.save(book(title));
    }

    @Transactional(propagation = TransactionDefinition.Propagation.NESTED)
    public void saveNestedAndFail(String title) {
        bookRepository.save(book(title));
        throw new IllegalStateException("Nested failure after " + title);
    }

    @Transactional(propagation = TransactionDefinition.Propagation.MANDATORY)
    public void saveMandatory(String title) {
        bookRepository.save(book(title));
    }

    private static Book book(String title) {
        Book book = new Book();
        book.setTitle(title);
        book.setTotalPages(10);
        return book;
    }
}
