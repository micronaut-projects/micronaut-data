package example;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

@TypeDef(type = DataType.INTEGER)
public class Quantity {
    private final int amount;

    private Quantity(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public static Quantity valueOf(int amount) {
        return new Quantity(amount);
    }
}
