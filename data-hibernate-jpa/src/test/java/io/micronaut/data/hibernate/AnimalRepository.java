package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.hibernate.entities.Animal;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, Long> {

    @Procedure(named = "myAdd1Named")
    int add1Named(int myInput);

    @Procedure(named = "myAdd1Indexed")
    int add1Indexed(int myInput);

    @Procedure
    int add1(int myInput);

    @Procedure("add1")
    int add1Alias(int myInput);

}
