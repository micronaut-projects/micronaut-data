/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.sql

class TestEntities {

    static String compositePrimaryKeyEntities() {
        '''
import java.time.LocalDateTime;

@Entity
class Project {
    @EmbeddedId
    private ProjectId projectId;
    private String name;

    public Project(ProjectId projectId, String name) {
        this.projectId = projectId;
        this.name = name;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }
}


@Embeddable
class ProjectId {
    @Column(name="department_id")
    private final int departmentId;
    @GeneratedValue
    private final int projectId;

    public ProjectId(int departmentId, int projectId) {
        this.departmentId = departmentId;
        this.projectId = projectId;
    }

    public int getDepartmentId() {
        return departmentId;
    }

    public int getProjectId() {
        return projectId;
    }
}

'''
    }

    static String compositeRelationPrimaryKeyEntities() {
        '''
@Entity
class Role {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    public Role(String name) {
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
}

@Entity
class User {

    @Id
    @GeneratedValue
    private Long id;

    private String email;

    public User(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

@Embeddable
class UserRoleId {

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private final User user;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private final Role role;

    public UserRoleId(User user, Role role) {
        this.user = user;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserRoleId userRoleId = (UserRoleId) o;
        return role.getId().equals(userRoleId.getRole().getId()) &&
                user.getId().equals(userRoleId.getUser().getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(role.getId(), user.getId());
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }
}

@Entity
class UserRole {

    @EmbeddedId
    private final UserRoleId id;

    public UserRole(UserRoleId id) {
        this.id = id;
    }

    public UserRoleId getId() {
        return id;
    }

    @Transient
    public User getUser() {
        return id.getUser();
    }

    @Transient
    public Role getRole() {
        return id.getRole();
    }
}
'''
    }
}
