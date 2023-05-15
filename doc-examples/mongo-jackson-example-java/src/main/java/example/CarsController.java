package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/cars")
public class CarsController {

    private final CarRepository carRepository;

    public CarsController(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    @Get("/manufacturerOneToOne")
    Car manufacturerOneToOne() {
        return carRepository.retrieveById(1L);
    }

    @Get("/manufacturerManyToOne")
    Car manufacturerManyToOne() {
        return carRepository.queryById(2L);
    }

    @Get("/manufacturersOneToMany")
    Car manufacturersOneToMany() {
        return carRepository.getById(3L);
    }

    @Get("/manufacturersManyToMany")
    Car manufacturersManyToMany() {
        Car car = carRepository.readById(4L);
        return car;
    }

}
