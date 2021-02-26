package example;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Entity
public class Account {
    @GeneratedValue
    @Id
    private Long id;
    private String username;
    private String password;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @PrePersist
    void encodePassword() {
        this.password = Base64.getEncoder()
                .encodeToString(this.password.getBytes(StandardCharsets.UTF_8));
    }
}
