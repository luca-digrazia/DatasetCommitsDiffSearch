/**
 * The MIT License
 * Copyright (c) 2012 TORCH GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graylog2.shared.bindings.providers;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.graylog2.plugin.BaseConfiguration;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.Executors;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class EventBusProvider implements Provider<EventBus> {
    private final BaseConfiguration configuration;

    @Inject
    public EventBusProvider(BaseConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public EventBus get() {
        return new AsyncEventBus("graylog2-eventbus",
                Executors.newFixedThreadPool(
                        configuration.getAsyncEventbusProcessors(),
                        new ThreadFactoryBuilder()
                                .setDaemon(true)
                                .setNameFormat(
                                        "eventbus-handler-%d")
                                .build()
                ));
    }
}
