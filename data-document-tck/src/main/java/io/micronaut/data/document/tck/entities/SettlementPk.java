/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.tck.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@Embeddable
public class SettlementPk {
    @MappedProperty(value = "code")
    private String code;
    @MappedProperty(value = "code_id")
    private Integer codeId;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private County county;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getCodeId() {
        return codeId;
    }

    public void setCodeId(Integer codeId) {
        this.codeId = codeId;
    }

    public County getCounty() {
        return county;
    }

    public void setCounty(County county) {
        this.county = county;
    }

}
