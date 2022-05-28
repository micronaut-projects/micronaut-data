
package example;

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
