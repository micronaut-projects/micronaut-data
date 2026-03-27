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

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class OrderPk implements Serializable {

    private String tenantId;
    private Long orderNo;

    public OrderPk() {
    }

    public OrderPk(String tenantId, Long orderNo) {
        this.tenantId = tenantId;
        this.orderNo = orderNo;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public String getTenantId() {
        return tenantId;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Long getOrderNo() {
        return orderNo;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setOrderNo(Long orderNo) {
        this.orderNo = orderNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderPk)) return false;
        OrderPk orderPk = (OrderPk) o;
        return Objects.equals(tenantId, orderPk.tenantId) &&
            Objects.equals(orderNo, orderPk.orderNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, orderNo);
    }
}
