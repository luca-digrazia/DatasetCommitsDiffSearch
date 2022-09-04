package io.dropwizard.jersey.optional;

import io.dropwizard.jersey.DefaultValueUtils;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.OptionalLong;

@Singleton
public class OptionalLongParamConverterProvider implements ParamConverterProvider {
    private final OptionalLongParamConverter paramConverter = new OptionalLongParamConverter();

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
                                              final Annotation[] annotations) {
        if (!OptionalLong.class.equals(rawType)) {
            return null;
        }
        final String defaultValue = DefaultValueUtils.getDefaultValue(annotations);
        return (ParamConverter<T>) ((defaultValue == null) ? paramConverter : new OptionalLongParamConverter(defaultValue));
    }

    public static class OptionalLongParamConverter implements ParamConverter<OptionalLong> {

        @Nullable
        private final String defaultValue;

        public OptionalLongParamConverter() {
            this(null);
        }

        public OptionalLongParamConverter(@Nullable String defaultValue) {
            this.defaultValue = defaultValue;
        }

        @SuppressWarnings("OptionalAssignedToNull")
        @Override
        @Nullable
        public OptionalLong fromString(final String value) {
            try {
                final long l = Long.parseLong(value);
                return OptionalLong.of(l);
            } catch (NullPointerException | NumberFormatException e) {
                if (defaultValue != null) {
                    // If an invalid default value is specified, we want to fail fast.
                    // This is the same behavior as DropWizard 1.3.x and matches Jersey's handling of @DefaultValue for Long.
                    if (defaultValue.equals(value)) {
                        throw e;
                    }
                    // In order to fall back to use a default value for an empty query param, we must return null here.
                    // This preserves backwards compatibility with DropWizard 1.3.x handling of empty query params.
                    if (value == null || value.isEmpty()) {
                        return null;
                    }
                }
                return OptionalLong.empty();
            }
        }

        @Override
        public String toString(final OptionalLong value) {
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }
            return value.isPresent() ? Long.toString(value.getAsLong()) : "";
        }
    }
}
