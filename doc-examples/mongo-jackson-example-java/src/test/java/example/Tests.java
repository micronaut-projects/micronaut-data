package example;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class Tests {

    @Inject
    CarRepository carRepository;
    @Inject
    CarManufacturer1Repository manufacturer1Repository;
    @Inject
    CarManufacturer2Repository manufacturer2Repository;
    @Inject
    CarManufacturer3Repository manufacturer3Repository;
    @Inject
    CarManufacturer4Repository manufacturer4Repository;
    @Inject
    ObjectIdEntityRepository objectIdEntityRepository;

    @Inject
    @Client("/")
    HttpClient httpClient;

    @AfterEach
    public void cleanup() {
        carRepository.deleteAll();
        manufacturer1Repository.deleteAll();
        manufacturer2Repository.deleteAll();
        manufacturer3Repository.deleteAll();
        manufacturer4Repository.deleteAll();
        objectIdEntityRepository.deleteAll();
    }

    @org.junit.jupiter.api.Test
    void test() {
        CarManufacturer1 manufacturerOneToOne = new CarManufacturer1(1L, "Tesla ONE_TO_ONE");
        manufacturer1Repository.save(manufacturerOneToOne);
        CarManufacturer2 manufacturerManyToOne = new CarManufacturer2(2L, "Tesla MANY_TO_ONE");
        manufacturer2Repository.save(manufacturerManyToOne);

        Car car1 = new Car(
            1L,
            "ONE_TO_ONE",
            manufacturerOneToOne,
            null,
            null,
            null
        );
        carRepository.save(car1);

        Car car2 = new Car(
            2L,
            "MANY_TO_ONE",
            null,
            manufacturerManyToOne,
            null,
            null
        );
        carRepository.save(car2);

        Car car3 = new Car(
            3L,
            "ONE_TO_MANY",
            null,
            null,
            null,
            null
        );
        carRepository.save(car3);

        CarManufacturer3 manufacturersOneToMany1 = new CarManufacturer3(3L, "Tesla ONE_TO_MANY 1", car3);
        CarManufacturer3 manufacturersOneToMany2 = new CarManufacturer3(4L, "Tesla ONE_TO_MANY 1", car3);
        manufacturer3Repository.save(manufacturersOneToMany1);
        manufacturer3Repository.save(manufacturersOneToMany2);

        Car car4 = new Car(
            4L,
            "MANY_TO_MANY",
            null,
            null,
            null,
            null
        );
        carRepository.save(car4);

        CarManufacturer4 manufacturersManyToMany1 = new CarManufacturer4(5L, "Tesla MANY_TO_MANY 1", Collections.singletonList(car4));
        CarManufacturer4 manufacturersManyToMany2 = new CarManufacturer4(6L, "Tesla MANY_TO_MANY 2", Collections.singletonList(car4));
        manufacturer4Repository.save(manufacturersManyToMany1);
        manufacturer4Repository.save(manufacturersManyToMany2);

        Car manufacturerOneToOneCar = httpClient.toBlocking().retrieve(HttpRequest.GET("/cars/manufacturerOneToOne"), Argument.of(Car.class));

        assertEquals(car1.name(), manufacturerOneToOneCar.name());
        assertEquals(car1.manufacturerOneToOne(), manufacturerOneToOneCar.manufacturerOneToOne());

        Car manufacturerManyToOneCar = httpClient.toBlocking().retrieve(HttpRequest.GET("/cars/manufacturerManyToOne"), Argument.of(Car.class));

        assertEquals(car2.name(), manufacturerManyToOneCar.name());
        assertEquals(car2.manufacturerManyToOne(), manufacturerManyToOneCar.manufacturerManyToOne());

        Car manufacturersOneToManyCar = httpClient.toBlocking().retrieve(HttpRequest.GET("/cars/manufacturersOneToMany"), Argument.of(Car.class));

        assertEquals(car3.name(), manufacturersOneToManyCar.name());
        assertTrue(manufacturersOneToManyCar.manufacturersOneToMany().stream().anyMatch(c -> c.name().equals(manufacturersOneToMany1.name())));
        assertTrue(manufacturersOneToManyCar.manufacturersOneToMany().stream().anyMatch(c -> c.name().equals(manufacturersOneToMany2.name())));

//        Car manufacturersManyToManyCar = httpClient.toBlocking().retrieve(HttpRequest.GET("/cars/manufacturersManyToMany"), Argument.of(Car.class));
//
//        assertTrue(manufacturersManyToManyCar.manufacturersManyToMany().stream().anyMatch(c -> c.name().equals(manufacturersManyToMany1.name())));
//        assertTrue(manufacturersManyToManyCar.manufacturersManyToMany().stream().anyMatch(c -> c.name().equals(manufacturersManyToMany2.name())));
    }

    @Disabled // TODO: Implement in Micronaut Serialization
    @Test
    public void testObjectIdDeser() {
        ObjectIdEntity entity = new ObjectIdEntity(null);
        objectIdEntityRepository.save(entity);

        List<ObjectIdEntity> entityList = httpClient.toBlocking().retrieve(HttpRequest.GET("/entities/objectId"), Argument.listOf(ObjectIdEntity.class));
        assertEquals(entity, entityList.get(0));
    }

}
