/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.example;

import jakarta.persistence.*;

@Access(AccessType.PROPERTY)
@Entity
public class EmployeePropertyAccess {
    private Long id;
    private String name;
    private double salary;

    public EmployeePropertyAccess(Long id, String name, double salary) {
        this.id = id;
        this.name = name;
        this.salary = salary;
    }

    public EmployeePropertyAccess() {
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    @Column(name = "name")
    public String getName() {
        return name;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setName(String name) {
        this.name = name;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    @Column(name = "salary")
    public double getSalary() {
        return salary;
    }

    public void setSalary(double salary) {
        this.salary = salary;
    }
}
