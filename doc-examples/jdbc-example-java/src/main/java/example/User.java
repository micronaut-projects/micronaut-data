package example;

import io.micronaut.data.annotation.*;

@MappedEntity
@Where("enabled = true") // <1>
public class User {
    @GeneratedValue
    @Id
    private Long id;
    private String name;
    private boolean enabled = true; // <2>

    public User(String name) {
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

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
