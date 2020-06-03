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

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import javax.inject.Singleton;
import javax.transaction.Transactional;

@Singleton
public class BookManager {
    private final BookRepository bookRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BookManager(BookRepository bookRepository, ApplicationEventPublisher eventPublisher) { // <1>
        this.bookRepository = bookRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    void saveBook(String title, int pages) {
        final Book book = new Book(title, pages);
        bookRepository.save(book);
        eventPublisher.publishEvent(new NewBookEvent(book)); // <2>
    }

    @TransactionalEventListener
    void onNewBook(NewBookEvent event) {
        System.out.println("book = " + event.book); // <3>
    }

    static class NewBookEvent {
        final Book book;

        public NewBookEvent(Book book) {
            this.book = book;
        }
    }
}
