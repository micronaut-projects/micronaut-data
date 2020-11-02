
package example;

import io.micronaut.core.annotation.Creator;

import javax.persistence.*;

@Entity
public class Manufacturer {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @Creator
    public Manufacturer(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

}
