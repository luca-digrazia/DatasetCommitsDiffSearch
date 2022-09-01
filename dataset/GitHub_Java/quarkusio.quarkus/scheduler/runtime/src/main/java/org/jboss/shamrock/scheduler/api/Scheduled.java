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
package org.jboss.shamrock.scheduler.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Marks a business method invocation to be automatically scheduled according to {@link Scheduled#cron()} or {@link Scheduled#every()} expression respectively.
 *
 * <pre>
 * &#64;ApplicationScoped
 * class MyService {
 *
 *     &#64;Scheduled(cron = "0/5 * * * * ?")
 *     void check() {
 *         // do something important every 5 seconds
 *     }
 * }
 * </pre>
 *
 * @author Martin Kouba
 * @see ScheduledExecution
 */
@Target(METHOD)
@Retention(RUNTIME)
@Repeatable(Scheduleds.class)
public @interface Scheduled {

    /**
     * If the value starts with "&#123;" and ends with "&#125;" the scheduler attempts to find a corresponding config property and use the configured value
     * instead: {@code &#64;Scheduled(cron = "{myservice.check.cron.expr}")}.
     *
     * @return the CRON expression
     */
    String cron() default "";

    /**
     * The value is parsed with {@link Duration#parse(CharSequence)}. However, if an expression starts with a digit, "PT" prefix is added automatically, so for
     * example, {@code 15m} can be used instead of {@code PT15M} and is parsed as "15 minutes". Note that the absolute value of the value is always used.
     * <p>
     * If the value starts with "&#123;" and ends with "&#125;" the scheduler attempts to find a corresponding config property and use the configured value
     * instead: {@code &#64;Scheduled(every = "{myservice.check.every.expr}")}.
     *
     * @return the period expression based on the ISO-8601 duration format {@code PnDTnHnMn.nS}
     */
    String every() default "";

    /**
     * Delays the time the trigger should start at. By default, the trigger starts when registered.
     *
     * @return the delay
     */
    long delay() default 0;

    /**
     *
     * @return the delay unit
     */
    TimeUnit delayUnit() default TimeUnit.MINUTES;

}
