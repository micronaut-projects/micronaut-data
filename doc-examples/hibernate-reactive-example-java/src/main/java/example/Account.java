package example;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.nio.charset.StandardCharsets;
import java.time.MonthDay;
import java.util.Base64;

@Entity
public class Account {
    @GeneratedValue
    @Id
    private Long id;
    private String username;
    private String password;
    @Column(columnDefinition = "timestamp")
    @Convert(converter = MonthDayDateAttributeConverter.class)
    private MonthDay paymentDay;

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

    public MonthDay getPaymentDay() {
        return paymentDay;
    }

    public void setPaymentDay(MonthDay paymentDay) {
        this.paymentDay = paymentDay;
    }

    @PrePersist
    void encodePassword() {
        this.password = Base64.getEncoder()
                .encodeToString(this.password.getBytes(StandardCharsets.UTF_8));
    }
}
