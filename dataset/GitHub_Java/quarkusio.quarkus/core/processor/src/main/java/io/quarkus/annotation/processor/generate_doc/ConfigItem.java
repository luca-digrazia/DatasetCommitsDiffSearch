package io.quarkus.annotation.processor.generate_doc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConfigItem implements Comparable<ConfigItem> {
    private static final Map<String, String> PRIMITIVE_DEFAULT_VALUES = new HashMap<>();

    static {
        PRIMITIVE_DEFAULT_VALUES.put("int", "0");
        PRIMITIVE_DEFAULT_VALUES.put("long", "0l");
        PRIMITIVE_DEFAULT_VALUES.put("float", "0f");
        PRIMITIVE_DEFAULT_VALUES.put("double", "0d");
        PRIMITIVE_DEFAULT_VALUES.put("boolean", "false");
    }

    private final String type;
    private final String javaDocKey;
    private final String propertyName;
    private final String defaultValue;
    private final ConfigVisibility visibility;

    public ConfigItem(String propertyName, String javaDocKey, String type, String defaultValue,
            ConfigVisibility visibility) {
        this.type = type;
        this.javaDocKey = javaDocKey;
        this.propertyName = propertyName;
        this.defaultValue = defaultValue;
        this.visibility = visibility;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConfigItem that = (ConfigItem) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(javaDocKey, that.javaDocKey) &&
                Objects.equals(propertyName, that.propertyName) &&
                Objects.equals(defaultValue, that.defaultValue) &&
                Objects.equals(visibility, that.visibility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, javaDocKey, propertyName, defaultValue, visibility);
    }

    @Override
    public String toString() {
        return "ConfigItem{" +
                "type='" + type + '\'' +
                ", javaDocKey='" + javaDocKey + '\'' +
                ", propertyName='" + propertyName + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                ", visibility='" + visibility + '\'' +
                '}';
    }

    @Override
    public int compareTo(ConfigItem o) {
        return propertyName.compareTo(o.propertyName);
    }

    public String getType() {
        return type;
    }

    public String getJavaDocKey() {
        return javaDocKey;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getDefaultValue() {
        if (!defaultValue.isEmpty()) {
            return defaultValue;
        }

        return PRIMITIVE_DEFAULT_VALUES.containsKey(type) ? PRIMITIVE_DEFAULT_VALUES.get(type) : "";
    }

    public ConfigVisibility getVisibility() {
        return visibility;
    }
}
