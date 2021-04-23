package example;

import org.testcontainers.containers.MySQLContainer;

public class MySqlServer {

    static MySQLContainer<?> start() {
        MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0.17");
        container.start();
        return container;
    }
}
