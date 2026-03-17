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

import jakarta.persistence.Embeddable;

@Embeddable
public class EmbeddableClass {

    String embeddedName;
    Long number;
    long n;
    double d;

    public EmbeddableClass() {
    }

    public EmbeddableClass(String embeddedName, Long number, long n, double d) {
        this.embeddedName = embeddedName;
        this.number = number;
        this.n = n;
        this.d = d;
    }

    @SuppressWarnings({"checkstyle:DesignForExtension"})
    public String getEmbeddedName() {
        return embeddedName;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setEmbeddedName(String embeddedName) {
        this.embeddedName = embeddedName;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Long getNumber() {
        return number;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setNumber(Long number) {
        this.number = number;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public long getN() {
        return n;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setN(long n) {
        this.n = n;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public double getD() {
        return d;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setD(double d) {
        this.d = d;
    }
}
