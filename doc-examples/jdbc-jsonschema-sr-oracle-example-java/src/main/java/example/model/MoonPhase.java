package example.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Example domain model. In a real project the JSON Schema would be generated at build time
 * from this class; for this example we provide a static schema resource too.
 */
public record MoonPhase(
        @NotBlank String phase,
        @NotBlank String emoji
) { }
