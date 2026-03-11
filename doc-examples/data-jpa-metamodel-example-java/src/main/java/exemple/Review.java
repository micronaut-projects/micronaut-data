/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package exemple;

import jakarta.persistence.*;

import java.awt.print.Book;

/**
 * A book review.
 *
 * @param id The id
 * @param reviewer The reviewer name
 * @param content The book review content
 * @param book The reviewed book
 */
@Entity
public record Review(

    @GeneratedValue
    @Id
    Long id,
    String reviewer,
    String content,
    @ManyToOne(fetch = FetchType.LAZY)
    Book book) {

    public Review(String reviewer, String content) {
        this(null, reviewer, content, null);
    }
}
