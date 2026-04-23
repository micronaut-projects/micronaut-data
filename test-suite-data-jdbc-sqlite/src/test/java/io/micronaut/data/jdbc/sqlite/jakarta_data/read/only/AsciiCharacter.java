package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import io.micronaut.core.annotation.Introspected;

import java.io.Serializable;

@jakarta.persistence.Entity
@Introspected(accessKind = {Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD}, visibility = Introspected.Visibility.ANY)
public class AsciiCharacter implements Serializable {
    private static final long serialVersionUID = 1L;

    @jakarta.persistence.Id
    private long id;

    private int numericValue;

    private String hexadecimal;

    private char thisCharacter;

    private boolean isControl;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(int numericValue) {
        this.numericValue = numericValue;
    }

    public String getHexadecimal() {
        return hexadecimal;
    }

    public void setHexadecimal(String hexadecimal) {
        this.hexadecimal = hexadecimal;
    }

    public char getThisCharacter() {
        return thisCharacter;
    }

    public void setThisCharacter(char thisCharacter) {
        this.thisCharacter = thisCharacter;
    }

    public boolean isControl() {
        return isControl;
    }

    public void setControl(boolean isControl) {
        this.isControl = isControl;
    }

}
