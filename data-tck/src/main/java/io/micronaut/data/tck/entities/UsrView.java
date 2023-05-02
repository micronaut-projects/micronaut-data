package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "USR_VIEW", alias = "uv")
@JsonView(table = "USR")
public class UsrView {
    @Id
    private Long usrId;
    private String name;
    private int age;

    public Long getUsrId() { return usrId; }
    public void setUsrId(Long usrId) { this.usrId = usrId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
