package example;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
}
