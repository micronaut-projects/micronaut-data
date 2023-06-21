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
