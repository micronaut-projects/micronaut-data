package example;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public class MySqlServer {

    static MySQLContainer<?> start() {
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse("mysql").withTag("5"));
        container.start();
        return container;
    }
}
