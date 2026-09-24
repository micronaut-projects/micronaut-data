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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.tck.entities.Author;

import java.util.List;

/**
 * Interface holding a set of repository interfaces with findAll query and different join types.
 */
public interface AuthorJoinTypeRepositories {
    interface AuthorJoinFetchRepository {
        @Join(value = "books", type = Join.Type.FETCH)
        List<Author> findAll();
    }

    interface AuthorJoinInnerRepository {
        @Join(value = "books", type = Join.Type.INNER)
        List<Author> findAll();
    }

    interface AuthorJoinLeftFetchRepository {
        @Join(value = "books", type = Join.Type.LEFT_FETCH)
        List<Author> findAll();
    }

    interface AuthorJoinLeftRepository {
        @Join(value = "books", type = Join.Type.LEFT)
        List<Author> findAll();

    }

    interface AuthorJoinOuterFetchRepository {
        @Join(value = "books", type = Join.Type.OUTER_FETCH)
        List<Author> findAll();
    }

    interface AuthorJoinOuterRepository {
        @Join(value = "books", type = Join.Type.OUTER)
        List<Author> findAll();
    }

    interface AuthorJoinRightFetchRepository {
        @Join(value = "books", type = Join.Type.RIGHT_FETCH)
        List<Author> findAll();
    }

    interface AuthorJoinRightRepository {
        @Join(value = "books", type = Join.Type.RIGHT)
        List<Author> findAll();
    }
}
