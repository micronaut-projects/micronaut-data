package example;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;

@Embeddable
public class MyPart {
    @GeneratedValue
    private String text;

    public MyPart() {
    }

    public MyPart(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}

