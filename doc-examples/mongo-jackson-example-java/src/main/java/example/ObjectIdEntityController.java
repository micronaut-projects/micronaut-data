package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

@Controller("/entities/objectId")
public class ObjectIdEntityController {

    private final ObjectIdEntityRepository objectIdEntityRepository;

    public ObjectIdEntityController(ObjectIdEntityRepository objectIdEntityRepository) {
        this.objectIdEntityRepository = objectIdEntityRepository;
    }

    @Get
    List<ObjectIdEntity> getAll() {
        return objectIdEntityRepository.findAll();
    }

}
