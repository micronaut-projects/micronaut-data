package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Author;

import javax.annotation.Nullable;

public interface AuthorRepository extends CrudRepository<Author, Long> {

    Author findByName(String name);

    Author findByBooksTitle(String title);

    long countByNameContains(String text);

    Author findByNameStartsWith(String name);

    Author findByNameEndsWith(String name);

    Author findByNameIgnoreCase(String name);

    @Join("books")
    Author searchByName(String name);

    void updateNickname(@Id Long id, @Parameter("nickName") @Nullable String nickName);
}