package io.micronaut.data.hibernate;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class PersonDto {
    private int age;

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
