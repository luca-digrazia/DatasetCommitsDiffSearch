/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;

final class OptionValuesImpl implements OptionValues {

    private static final float FUZZY_MATCH_THRESHOLD = 0.7F;

    // TODO is this too long? Make sure to update Engine#setUseSystemProperties javadoc.
    // Just using graalvm as prefix is not good enough as it is ambiguous with host compilation
    // For example the property graalvm.compiler is an option for which compiler. The java compiler?
    // or the truffle compiler?
    static final String SYSTEM_PROPERTY_PREFIX = "polyglot.";

    private final PolyglotEngineImpl engine;
    private final OptionDescriptors descriptors;
    private final Map<OptionKey<?>, Object> values;

    OptionValuesImpl(PolyglotEngineImpl engine, OptionDescriptors descriptors) {
        this.engine = engine;
        this.descriptors = descriptors;
        this.values = new HashMap<>();
    }

    @Override
    public int hashCode() {
        int result = 31 + descriptors.hashCode();
        result = 31 * result + engine.hashCode();
        result = 31 * result + values.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OptionValues)) {
            return super.equals(obj);
        } else {
            if (this == obj) {
                return true;
            }
            OptionValues other = ((OptionValues) obj);
            if (getDescriptors().equals(other.getDescriptors())) {
                if (!hasSetOptions() && !other.hasSetOptions()) {
                    return true;
                }
                if (other instanceof OptionValuesImpl) {
                    // faster comparison that only depends on the set values
                    for (OptionKey<?> key : values.keySet()) {
                        if (hasBeenSet(key) || other.hasBeenSet(key)) {
                            if (!get(key).equals(other.get(key))) {
                                return false;
                            }
                        }
                    }
                    for (OptionKey<?> key : ((OptionValuesImpl) other).values.keySet()) {
                        if (hasBeenSet(key) || other.hasBeenSet(key)) {
                            if (!get(key).equals(other.get(key))) {
                                return false;
                            }
                        }
                    }
                    return true;
                } else {
                    // slow comparison for arbitrary option values
                    for (OptionDescriptor descriptor : getDescriptors()) {
                        OptionKey<?> key = descriptor.getKey();
                        if (hasBeenSet(key) || other.hasBeenSet(key)) {
                            if (!get(key).equals(other.get(key))) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    public void putAll(Map<String, String> providedValues, boolean allowExperimentalOptions) {
        for (String key : providedValues.keySet()) {
            put(key, providedValues.get(key), allowExperimentalOptions);
        }
    }

    public void put(String key, String value, boolean allowExperimentalOptions) {
        OptionDescriptor descriptor = findDescriptor(key, allowExperimentalOptions);
        values.put(descriptor.getKey(), descriptor.getKey().getType().convert(value));
    }

    private OptionValuesImpl(OptionValuesImpl copy) {
        this.engine = copy.engine;
        this.values = new HashMap<>(copy.values);
        this.descriptors = copy.descriptors;
    }

    public boolean hasBeenSet(OptionKey<?> optionKey) {
        return values.containsKey(optionKey);
    }

    OptionValuesImpl copy() {
        return new OptionValuesImpl(this);
    }

    public OptionDescriptors getDescriptors() {
        return descriptors;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(OptionKey<T> optionKey) {
        Object value = values.get(optionKey);
        if (value == null) {
            return optionKey.getDefaultValue();
        }
        return (T) value;
    }

    @Override
    public <T> void set(OptionKey<T> optionKey, T value) {
        optionKey.getType().validate(value);
        values.put(optionKey, value);
    }

    @Override
    public boolean hasSetOptions() {
        return !values.isEmpty();
    }

    private OptionDescriptor findDescriptor(String key, boolean allowExperimentalOptions) {
        OptionDescriptor descriptor = descriptors.get(key);
        if (descriptor == null) {
            throw failNotFound(key);
        }
        if (!allowExperimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw failExperimental(key);
        }
        return descriptor;
    }

    private static RuntimeException failExperimental(String key) {
        final String message = String.format("Option '%s' is experimental and must be enabled with allowExperimentalOptions().%n", key) +
                        "Do not use experimental options in production environments.";
        return new IllegalArgumentException(message);
    }

    private RuntimeException failNotFound(String key) {
        OptionDescriptors allOptions;
        Exception errorOptions = null;
        try {
            allOptions = engine == null ? this.descriptors : this.engine.getAllOptions();
        } catch (Exception e) {
            errorOptions = e;
            allOptions = this.descriptors;
        }
        RuntimeException error = failNotFound(allOptions, key);
        if (errorOptions != null) {
            error.addSuppressed(errorOptions);
        }

        throw error;
    }

    static RuntimeException failNotFound(OptionDescriptors allOptions, String key) {
        Iterable<OptionDescriptor> matches = fuzzyMatch(allOptions, key);
        Formatter msg = new Formatter();
        msg.format("Could not find option with name %s.", key);

        Iterator<OptionDescriptor> iterator = matches.iterator();
        if (iterator.hasNext()) {
            msg.format("%nDid you mean one of the following?");
            for (OptionDescriptor match : matches) {
                msg.format("%n    %s=<%s>", match.getName(), match.getKey().getType().getName());
            }
        }
        throw new IllegalArgumentException(msg.toString());
    }

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    static List<OptionDescriptor> fuzzyMatch(OptionDescriptors descriptors, String optionKey) {
        List<OptionDescriptor> matches = new ArrayList<>();
        for (org.graalvm.options.OptionDescriptor option : descriptors) {
            float score = stringSimiliarity(option.getName(), optionKey);
            if (score >= FUZZY_MATCH_THRESHOLD) {
                matches.add(option);
            }
        }
        return matches;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     *
     * Ported from str_similar() in globals.cpp.
     */
    private static float stringSimiliarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }
}
