/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.tck.services;

import io.micronaut.data.tck.entities.Train;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.data.event.LifecycleEvent;
import jakarta.data.event.PostDeleteEvent;
import jakarta.data.event.PostInsertEvent;
import jakarta.data.event.PostUpdateEvent;
import jakarta.data.event.PreDeleteEvent;
import jakarta.data.event.PreInsertEvent;
import jakarta.data.event.PreUpdateEvent;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class JakartaDataTrainEventListener {

    private final List<String> events = new ArrayList<>();

    @EventListener
    public void onEvent(PostDeleteEvent<Train> event) {
        assertEvent(event);
        events.add("PostDeleteEvent");
    }

    @EventListener
    public void onEvent(PreDeleteEvent<Train> train) {
        assertEvent(train);
        events.add("PreDeleteEvent");
    }

    @EventListener
    public void onEvent(PreUpdateEvent<Train> train) {
        assertEvent(train);
        events.add("PreUpdateEvent");
    }

    @EventListener
    public void onEvent(PostUpdateEvent<Train> train) {
        assertEvent(train);
        events.add("PostUpdateEvent");
    }

    @EventListener
    public void onEvent(PreInsertEvent<Train> train) {
        assertEvent(train);
        events.add("PreInsertEvent");
    }

    @EventListener
    public void onEvent(PostInsertEvent<Train> train) {
        assertEvent(train);
        events.add("PostInsertEvent");
    }

    public List<String> getEvents() {
        return events;
    }

    private void assertEvent(LifecycleEvent<Train> event) {
        if (!(event.entity() instanceof Train)) {
            throw new IllegalArgumentException("Entity is not a Train");
        }
    }
}
