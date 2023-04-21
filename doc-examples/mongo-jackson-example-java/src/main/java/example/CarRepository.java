package example;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@MongoRepository
public interface CarRepository extends CrudRepository<Car, Long> {

    @Join("manufacturerOneToOne")
    Car retrieveById(Long id);

    @Join("manufacturerManyToOne")
    Car queryById(Long id);

    @Join("manufacturersOneToMany")
    Car getById(Long id);

    @Join("manufacturersManyToMany")
    Car readById(Long id);
}
