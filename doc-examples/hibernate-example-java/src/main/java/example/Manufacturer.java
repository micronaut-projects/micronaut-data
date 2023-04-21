
package example;

import io.micronaut.configuration.hibernate.jpa.proxy.GenerateProxy;
import org.hibernate.annotations.BatchSize;

import jakarta.persistence.*;

@Entity
@GenerateProxy
@BatchSize(size = 10)
public class Manufacturer {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
