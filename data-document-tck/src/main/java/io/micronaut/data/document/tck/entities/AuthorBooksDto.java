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
package io.micronaut.data.document.tck.entities;

import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Introspected
public class AuthorBooksDto {

    @Nullable
    private String authorName;

    @Nullable
    private List<BookDto> books;

    public AuthorBooksDto() {
    }

    public AuthorBooksDto(String authorName, List<BookDto> books) {
        this.authorName = authorName;
        this.books = books;
    }

    @Nullable
    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    @Nullable
    public List<BookDto> getBooks() {
        return books;
    }

    public void setBooks(List<BookDto> books) {
        this.books = books;
    }
}
