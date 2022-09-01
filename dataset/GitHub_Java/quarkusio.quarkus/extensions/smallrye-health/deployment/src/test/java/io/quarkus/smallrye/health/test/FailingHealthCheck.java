/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quarkus.smallrye.health.test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.Dependent;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

@Dependent
@Health
public class FailingHealthCheck implements HealthCheck {

	@Override
    public HealthCheckResponse call() {
        return new HealthCheckResponse() {
            @Override
            public String getName() {
                return "failing";
            }

            @Override
            public State getState() {
                return State.DOWN;
            }

            @Override
            public Optional<Map<String, Object>> getData() {
                return Optional.of(Collections.singletonMap("status", "all broken"));
            }
        };
    }
}
