/*
 *
 *  * Copyright 2016 http://www.hswebframework.org
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.hswebframework.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhouhao on 16-5-26.
 */
public class ThreadLocalUtils {
    private static final ThreadLocal<Map<String, Object>> local = ThreadLocal.withInitial(() -> new HashMap<>());

    public static <T> T put(String key, T value) {
        local.get().put(key, value);
        return value;
    }

    public static void remove(String key) {
        local.get().remove(key);
    }

    public static void clear() {
        local.remove();
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return ((T) local.get().get(key));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAndRemove(String key) {
        try {
            return ((T) local.get().get(key));
        } finally {
            local.get().remove(key);
        }
    }
}
