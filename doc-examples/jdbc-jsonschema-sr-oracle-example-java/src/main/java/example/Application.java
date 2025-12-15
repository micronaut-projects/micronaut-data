package example;

import io.micronaut.runtime.Micronaut;

/**
 * Minimal Micronaut application used for the JSON Schema Registry + Oracle domain example.
 */
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
