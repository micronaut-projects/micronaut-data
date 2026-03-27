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

package com.example.repository.specification;

import com.example.Category_;
import com.example.Client;
import com.example.Client_;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import jakarta.persistence.criteria.JoinType;

public class ClientSpecification {
    public static PredicateSpecification<Client> tierEquals(Client.Tier tier) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Client_.tier), tier);
    }

    public static PredicateSpecification<Client> nameEquals(String name) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Client_.name), name);
    }

    public static PredicateSpecification<Client> withCategoryListName(String name) {
        return (root, criteriaBuilder) -> {
            var category = root.join(Client_.categoriesList, JoinType.INNER);
            return criteriaBuilder.equal(category.get(Category_.name), name);
        };
    }

    public static PredicateSpecification<Client> withCategorySetName(String name) {
        return (root, criteriaBuilder) -> {
            var category = root.join(Client_.categoriesSet, JoinType.INNER);
            return criteriaBuilder.equal(category.get(Category_.name), name);
        };
    }

    public static PredicateSpecification<Client> mainCategoryIdEquals(Long id) {
        return ((root, criteriaBuilder) -> {
            var category = root.join(Client_.mainCategory, JoinType.INNER);
            return criteriaBuilder.equal(category.get(Category_.id), id);
        });
    }

//    public static  PredicateSpecification<Client> withEntryEquals(Map.Entry<String, String> entry) {
//        return ((root, criteriaBuilder) -> {
//            var propsJoin = root.join(Client_.properties);
//            return criteriaBuilder.equal(propsJoin.entry(), entry);
//        });
//    }
}
