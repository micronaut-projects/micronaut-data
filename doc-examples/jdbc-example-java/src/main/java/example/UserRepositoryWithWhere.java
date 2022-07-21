package example;

import io.micronaut.data.annotation.Where;

import java.util.List;

public interface UserRepositoryWithWhere {

    // ...

    @Where("@.enabled = false")
    List<User> findDisabled();
}
