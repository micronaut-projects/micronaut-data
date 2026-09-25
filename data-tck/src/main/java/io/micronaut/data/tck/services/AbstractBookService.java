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
package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;
import io.micronaut.data.tck.repositories.SimpleBookRepository;

import jakarta.transaction.Transactional;

public abstract class AbstractBookService {

    public static final String BOOK_REPOSITORY_CLASS_PROPERTY = "bookRepositoryClass";

    protected final SimpleBookRepository bookRepository;

    public AbstractBookService(ApplicationContext beanContext) {
        Class<? extends BookRepository> bookRepositoryClass = (Class<? extends BookRepository>) ClassUtils.forName(
            beanContext.getProperty(BOOK_REPOSITORY_CLASS_PROPERTY, String.class).orElseThrow(),
            beanContext.getClassLoader()
        ).orElseThrow();
        this.bookRepository = beanContext.getBean(bookRepositoryClass);
    }

    public SimpleBookRepository getBookRepository() {
        return bookRepository;
    }

    @Transactional
    public long countBooksTransactional() {
        return bookRepository.count();
    }

    public long countBooks() {
        return bookRepository.count();
    }

    public void cleanup() {
        bookRepository.deleteAll();
    }

    protected Book newBook(String bookName) {
        Book book = new Book();
        book.setTitle(bookName);
        book.setTotalPages(1000);
        return book;
    }

}
