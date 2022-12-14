/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.SortedSet;
import java.util.TreeSet;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionValuesAccess;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.MapCursor;

import jdk.vm.ci.common.InitTimer;

/**
 * The {@link #HOTSPOT_OPTIONS} value contains the options values initialized in a HotSpot VM. The
 * values are set via system properties with the {@value #GRAAL_OPTION_PROPERTY_PREFIX} prefix.
 */
@ServiceProvider(OptionValuesAccess.class)
public class HotSpotGraalOptionValues implements OptionValuesAccess {

    /**
     * The name of the system property specifying a file containing extra Graal option settings.
     */
    private static final String GRAAL_OPTIONS_FILE_PROPERTY_NAME = "graal.options.file";

    /**
     * The name of the system property specifying the Graal version.
     */
    private static final String GRAAL_VERSION_PROPERTY_NAME = "graal.version";

    /**
     * The prefix for system properties that correspond to {@link Option} annotated fields. A field
     * named {@code MyOption} will have its value set from a system property with the name
     * {@code GRAAL_OPTION_PROPERTY_PREFIX + "MyOption"}.
     */
    public static final String GRAAL_OPTION_PROPERTY_PREFIX = "graal.";

    /**
     * Gets the system property assignment that would set the current value for a given option.
     */
    public static String asSystemPropertySetting(OptionValues options, OptionKey<?> value) {
        return GRAAL_OPTION_PROPERTY_PREFIX + value.getName() + "=" + value.getValue(options);
    }

    private static Properties getSavedProperties() {
        try {
            String value = System.getProperty("java.specification.version");
            if (value.startsWith("1.")) {
                value = value.substring(2);
            }
            int javaVersion = Integer.parseInt(value);
            String vmClassName = javaVersion <= 8 ? "sun.misc.VM" : "jdk.internal.misc.VM";
            Class<?> vmClass = Class.forName(vmClassName);
            Field savedPropsField = vmClass.getDeclaredField("savedProps");
            savedPropsField.setAccessible(true);
            return (Properties) savedPropsField.get(null);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    public static final OptionValues HOTSPOT_OPTIONS = initializeOptions();

    /**
     * Global options. The values for these options are initialized by parsing the file denoted by
     * the {@code VM.getSavedProperty(String) saved} system property named
     * {@value #GRAAL_OPTIONS_FILE_PROPERTY_NAME} if the file exists followed by parsing the options
     * encoded in saved system properties whose names start with
     * {@value #GRAAL_OPTION_PROPERTY_PREFIX}. Key/value pairs are parsed from the aforementioned
     * file with {@link Properties#load(java.io.Reader)}.
     */
    @SuppressWarnings("try")
    private static OptionValues initializeOptions() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        try (InitTimer t = timer("InitializeOptions")) {

            ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
            Properties savedProps = getSavedProperties();
            String optionsFile = savedProps.getProperty(GRAAL_OPTIONS_FILE_PROPERTY_NAME);

            if (optionsFile != null) {
                File graalOptions = new File(optionsFile);
                if (graalOptions.exists()) {
                    try (FileReader fr = new FileReader(graalOptions)) {
                        Properties props = new Properties();
                        props.load(fr);
                        EconomicMap<String, String> optionSettings = EconomicMap.create();
                        MapCursor<String, String> cursor = optionSettings.getEntries();
                        while (cursor.advance()) {
                            optionSettings.put(cursor.getKey(), cursor.getValue());
                        }
                        try {
                            OptionsParser.parseOptions(optionSettings, values, loader);
                        } catch (Throwable e) {
                            throw new InternalError("Error parsing an option from " + graalOptions, e);
                        }
                    } catch (IOException e) {
                        throw new InternalError("Error reading " + graalOptions, e);
                    }
                }
            }

            EconomicMap<String, String> optionSettings = EconomicMap.create();
            for (Map.Entry<Object, Object> e : savedProps.entrySet()) {
                String name = (String) e.getKey();
                if (name.startsWith(GRAAL_OPTION_PROPERTY_PREFIX)) {
                    if (name.equals("graal.PrintFlags") || name.equals("graal.ShowFlags")) {
                        System.err.println("The " + name + " option has been removed and will be ignored. Use -XX:+JVMCIPrintProperties instead.");
                    } else if (name.equals(GRAAL_OPTIONS_FILE_PROPERTY_NAME) || name.equals(GRAAL_VERSION_PROPERTY_NAME)) {
                        // Ignore well known properties that do not denote an option
                    } else {
                        String value = (String) e.getValue();
                        optionSettings.put(name.substring(GRAAL_OPTION_PROPERTY_PREFIX.length()), value);
                    }
                }
            }

            OptionsParser.parseOptions(optionSettings, values, loader);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    SortedSet<OptionKey<?>> sortedOptions = new TreeSet<>(new Comparator<OptionKey<?>>() {

                        @Override
                        public int compare(OptionKey<?> o1, OptionKey<?> o2) {
                            return o1.getReads() - o2.getReads();
                        }

                    });
                    for (OptionDescriptors opts : loader) {
                        for (OptionDescriptor desc : opts) {
                            sortedOptions.add(desc.getOptionKey());
                        }
                    }
                    for (OptionKey<?> k : sortedOptions) {
                        System.out.printf("%d\t%s%n", k.getReads(), k);
                    }
                }
            });

            return new OptionValues(values);
        }
    }

    @Override
    public OptionValues getOptions() {
        return HOTSPOT_OPTIONS;
    }
}
