package org.hswebframework.web.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.hswebframework.utils.time.DateFormatter;
import org.hswebframework.web.dict.EnumDict;
import org.hswebframework.web.proxy.Proxy;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zhouhao
 * @since 3.0
 */
@Slf4j
public final class FastBeanCopier {
    private static final Map<CacheKey, Copier> CACHE = new HashMap<>();

    private static final PropertyUtilsBean propertyUtils = BeanUtilsBean.getInstance().getPropertyUtils();

    private static final Map<Class, Class> wrapperClassMapping = new HashMap<>();

    public static final DefaultConvert DEFAULT_CONVERT = new DefaultConvert();

    static {
        wrapperClassMapping.put(byte.class, Byte.class);
        wrapperClassMapping.put(short.class, Short.class);
        wrapperClassMapping.put(int.class, Integer.class);
        wrapperClassMapping.put(float.class, Float.class);
        wrapperClassMapping.put(double.class, Double.class);
        wrapperClassMapping.put(char.class, Character.class);
        wrapperClassMapping.put(boolean.class, Boolean.class);
        wrapperClassMapping.put(long.class, Long.class);
    }

    public static <T, S> T copy(S source, T target, String... ignore) {
        return copy(source, target, DEFAULT_CONVERT, ignore);
    }

    public static <T, S> T copy(S source, Supplier<T> target, String... ignore) {
        return copy(source, target.get(), DEFAULT_CONVERT, ignore);
    }

    public static <T, S> T copy(S source, Class<T> target, String... ignore) {
        try {
            return copy(source, target.newInstance(), DEFAULT_CONVERT, ignore);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static Copier getCopier(Object source, Object target, boolean autoCreate) {
        Class sourceType = source instanceof Map ? Map.class : ClassUtils.getUserClass(source);
        Class targetType = target instanceof Map ? Map.class : ClassUtils.getUserClass(target);
        CacheKey key = createCacheKey(sourceType, targetType);
        if (autoCreate) {
            return CACHE.computeIfAbsent(key, k -> createCopier(sourceType, targetType));
        } else {
            return CACHE.get(key);
        }

    }

    public static <T, S> T copy(S source, T target, Converter converter, String... ignore) {
        if (source instanceof Map && target instanceof Map) {
            ((Map) target).putAll(((Map) source));
            return target;
        }
        getCopier(source, target, true)
                .copy(source, target, (ignore == null || ignore.length == 0) ? Collections.emptySet() : new HashSet<>(Arrays.asList(ignore)), converter);
        return target;
    }

    private static CacheKey createCacheKey(Class source, Class target) {
        return new CacheKey(source, target);
    }

    public static Copier createCopier(Class source, Class target) {
        String method = "public void copy(Object s, Object t, java.util.Set ignore, " +
                "org.hswebframework.web.bean.Converter converter){\n" +
                "try{\n\t" +
                source.getName() + " source=(" + source.getName() + ")s;\n\t" +
                target.getName() + " target=(" + target.getName() + ")t;\n\t" +
                createCopierCode(source, target) +
                "}catch(Exception e){\n" +
                "\tthrow new RuntimeException(e.getMessage(),e);" +
                "\n}\n" +
                "\n}";
        try {
            return Proxy.create(Copier.class)
                    .addMethod(method)
                    .newInstance();
        } catch (Exception e) {
            log.error("??????bean copy ??????????????????:\n{}", method, e);
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    private static Map<String, ClassProperty> createProperty(Class type) {
        return Stream.of(propertyUtils.getPropertyDescriptors(type))
                .filter(property -> !property.getName().equals("class") && property.getReadMethod() != null && property.getWriteMethod() != null)
                .map(BeanClassProperty::new)
                .collect(Collectors.toMap(ClassProperty::getName, Function.identity()));

    }

    private static Map<String, ClassProperty> createMapProperty(Map<String, ClassProperty> template) {
        return template.values().stream().map(classProperty -> new MapClassProperty(classProperty.name))
                .collect(Collectors.toMap(ClassProperty::getName, Function.identity()));
    }

    private static String createCopierCode(Class source, Class target) {
        Map<String, ClassProperty> sourceProperties = null;

        Map<String, ClassProperty> targetProperties = null;

        //????????????Map
        if (Map.class.isAssignableFrom(source)) {
            if (!Map.class.isAssignableFrom(target)) {
                targetProperties = createProperty(target);
                sourceProperties = createMapProperty(targetProperties);

            }
        } else if (Map.class.isAssignableFrom(target)) {
            if (!Map.class.isAssignableFrom(source)) {
                sourceProperties = createProperty(source);
                targetProperties = createMapProperty(sourceProperties);

            }
        } else {
            targetProperties = createProperty(target);
            sourceProperties = createProperty(source);
        }
        if (sourceProperties == null || targetProperties == null) {
            throw new UnsupportedOperationException("??????????????????,source:" + source + " target:" + target);
        }
        StringBuilder code = new StringBuilder();

        for (ClassProperty sourceProperty : sourceProperties.values()) {
            ClassProperty targetProperty = targetProperties.get(sourceProperty.getName());
            if (targetProperty == null) {
                continue;
            }
            code.append("if(!ignore.contains(\"").append(sourceProperty.getName()).append("\")){\n\t");
            if (!sourceProperty.isPrimitive()) {
                code.append("if(source.").append(sourceProperty.getReadMethod()).append("!=null){\n");
            }
            code.append(targetProperty.generateVar(targetProperty.getName())).append("=").append(sourceProperty.generateGetter(targetProperty.getType()))
                    .append(";\n");

            if (!targetProperty.isPrimitive()) {
                code.append("\tif(").append(sourceProperty.getName()).append("!=null){\n");
            }
            code.append("\ttarget.").append(targetProperty.generateSetter(targetProperty.getType(), sourceProperty.getName())).append(";\n");
            if (!targetProperty.isPrimitive()) {
                code.append("\t}\n");
            }
            if (!sourceProperty.isPrimitive()) {
                code.append("\t}\n");
            }
            code.append("}\n");
        }
        return code.toString();
    }

    static abstract class ClassProperty {

        @Getter
        protected String name;

        @Getter
        protected String readMethodName;

        @Getter
        protected String writeMethodName;

        @Getter
        protected Function<Class, String> getter;

        @Getter
        protected BiFunction<Class, String, String> setter;

        @Getter
        protected Class type;

        public String getReadMethod() {
            return readMethodName + "()";
        }

        public String generateVar(String name) {
            return getTypeName().concat(" ").concat(name);
        }

        public String getTypeName() {
            return getTypeName(type);
        }

        public String getTypeName(Class type) {
            String targetTypeName = type.getName();
            if (type.isArray()) {
                targetTypeName = type.getComponentType().getName() + "[]";
            }
            return targetTypeName;
        }

        public boolean isPrimitive() {
            return isPrimitive(getType());
        }

        public boolean isPrimitive(Class type) {
            return type.isPrimitive();
        }

        public boolean isWrapper() {
            return isWrapper(getType());
        }

        public boolean isWrapper(Class type) {
            return wrapperClassMapping.values().contains(type);
        }

        protected Class getPrimitiveType(Class type) {
            return wrapperClassMapping.entrySet().stream()
                    .filter(entry -> entry.getValue() == type)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        protected Class getWrapperType() {
            return wrapperClassMapping.get(type);
        }

        protected String castWrapper(String getter) {
            return getWrapperType().getSimpleName().concat(".valueOf(").concat(getter).concat(")");
        }

        public Function<Class, String> createGetterFunction() {

            return (targetType) -> {
                String getterCode = "source." + getReadMethod();

                String convert = "converter.convert((Object)(" + (isPrimitive() ? castWrapper(getterCode) : getterCode) + "),"
                        + getTypeName(targetType) + ".class)";
                StringBuilder convertCode = new StringBuilder();

                if (targetType != getType()) {
                    if (isPrimitive(targetType)) {
                        boolean sourceIsWrapper = isWrapper();
                        Class targetWrapperClass = wrapperClassMapping.get(targetType);

                        Class sourcePrimitive = getPrimitiveType(getType());
                        //?????????????????????????????????,???????????????????????????
                        // source.getField().intValue();
                        if (sourceIsWrapper) {
                            convertCode
                                    .append(getterCode)
                                    .append(".")
                                    .append(sourcePrimitive.getName())
                                    .append("Value()");
                        } else {
                            //????????????????????????convert??????
                            convertCode.append("((").append(targetWrapperClass.getName())
                                    .append(")")
                                    .append(convert)
                                    .append(").")
                                    .append(targetType.getName())
                                    .append("Value()");
                        }

                    } else if (isPrimitive()) {
                        boolean targetIsWrapper = isWrapper(targetType);
                        //?????????????????????????????????????????????????????????????????????
                        if (targetIsWrapper) {
                            convertCode.append(targetType.getName())
                                    .append(".valueOf(")
                                    .append(getterCode)
                                    .append(")");
                        } else {
                            convertCode.append("(").append(targetType.getName())
                                    .append(")(")
                                    .append(convert)
                                    .append(")");
                        }
                    } else {
                        convertCode.append("(").append(getTypeName(targetType))
                                .append(")(")
                                .append(convert)
                                .append(")");
                    }
                } else {
                    if (Cloneable.class.isAssignableFrom(targetType)) {
                        try {
//                            targetType.getDeclaredMethod("clone");
                            convertCode.append("(" + getTypeName() + ")").append(getterCode).append(".clone()");
                        } catch (Exception e) {
                            convertCode.append(getterCode);
                        }
                    } else {
                        convertCode.append(getterCode);
                    }

                }
//                if (!isPrimitive()) {
//                    return getterCode + "!=null?" + convertCode.toString() + ":null";
//                }
                return convertCode.toString();
            };
        }

        public BiFunction<Class, String, String> createSetterFunction(Function<String, String> settingNameSupplier) {
            return (sourceType, paramGetter) -> settingNameSupplier.apply(paramGetter);
        }

        public String generateGetter(Class targetType) {
            return getGetter().apply(targetType);
        }

        public String generateSetter(Class targetType, String getter) {
            return getSetter().apply(targetType, getter);
        }
    }

    static class BeanClassProperty extends ClassProperty {
        public BeanClassProperty(PropertyDescriptor descriptor) {
            type = descriptor.getPropertyType();
            readMethodName = descriptor.getReadMethod().getName();
            writeMethodName = descriptor.getWriteMethod().getName();

            getter = createGetterFunction();
            setter = createSetterFunction(paramGetter -> writeMethodName + "(" + paramGetter + ")");
            name = descriptor.getName();
        }
    }

    static class MapClassProperty extends ClassProperty {
        public MapClassProperty(String name) {
            type = Object.class;
            this.name = name;
            this.readMethodName = "get";
            this.writeMethodName = "put";

            this.getter = createGetterFunction();
            this.setter = createSetterFunction(paramGetter -> "put(\"" + name + "\"," + paramGetter + ")");
        }

        @Override
        public String getReadMethod() {
            return "get(\"" + name + "\")";
        }

        @Override
        public String getReadMethodName() {
            return "get(\"" + name + "\")";
        }
    }


    static final class DefaultConvert implements Converter {

        public Collection newCollection(Class targetClass) {

            if (targetClass == List.class) {
                return new ArrayList();
            } else if (targetClass == Set.class) {
                return new HashSet();
            } else if (targetClass == Queue.class) {
                return new LinkedList();
            } else {
                try {
                    return (Collection) targetClass.newInstance();
                } catch (Exception e) {
                    throw new UnsupportedOperationException("??????????????????:" + targetClass, e);
                }
            }
        }

        @Override
        @SuppressWarnings("all")
        public <T> T convert(Object source, Class<T> targetClass) {
            if (source == null) {
                return null;
            }
            if (source.getClass().isEnum()) {
                if (source instanceof EnumDict) {
                    Object val = (T) ((EnumDict) source).getValue();
                    if (targetClass.isInstance(val)) {
                        return ((T) val);
                    }
                    return convert(val, targetClass);
                }
            }
            if (targetClass == String.class) {
                if (source instanceof Date) {
                    // TODO: 18-4-16 ???????????????
                    return (T) DateFormatter.toString(((Date) source), "yyyy-MM-dd HH:mm:ss");
                }
                return (T) String.valueOf(source);
            }
            if (targetClass == Object.class) {
                return (T) source;
            }
            if (targetClass == Date.class) {
                if (source instanceof String) {
                    return (T) DateFormatter.fromString((String) source);
                }
                if (source instanceof Number) {
                    return (T) new Date(((Number) source).longValue());
                }
                if (source instanceof Date) {
                    return (T) new Date(((Date) source).getTime());
                }
            }
            org.apache.commons.beanutils.Converter converter = BeanUtilsBean
                    .getInstance()
                    .getConvertUtils()
                    .lookup(targetClass);
            if (null != converter) {
                return converter.convert(targetClass, source);
            }
            if (Collection.class.isAssignableFrom(targetClass)) {
                Collection collection = newCollection(targetClass);
                if (source instanceof Collection) {
                    collection.addAll(((Collection) source));
                } else if (source instanceof Object[]) {
                    collection.addAll(Arrays.asList(((Object[]) source)));
                } else {
                    if (source instanceof String) {
                        String stringValue = ((String) source);
                        collection.addAll(Arrays.asList(stringValue.split("[,]")));
                    } else {
                        collection.add(source);
                    }
                }
                return (T) collection;
            }

            if (targetClass.isEnum()) {
                if (EnumDict.class.isAssignableFrom(targetClass)) {
                    Object val = EnumDict.find((Class) targetClass, String.valueOf(source)).orElse(null);

                    if (targetClass.isInstance(val)) {
                        return ((T) val);
                    }
                    return convert(val, targetClass);
                }
                for (T t : targetClass.getEnumConstants()) {
                    if (((Enum) t).name().equalsIgnoreCase(String.valueOf(source))) {
                        return t;
                    }
                }
                log.warn("?????????:{}????????????:{}", source, targetClass);
                return null;
            }
            try {

                T newTarget = targetClass == Map.class ? (T) new HashMap<>() : targetClass.newInstance();
                copy(source, newTarget);
                return newTarget;
            } catch (Exception e) {
                log.warn("????????????{}->{}??????", source, targetClass, e);
                throw new UnsupportedOperationException(e.getMessage(), e);
            }
//            return null;
        }
    }

    @AllArgsConstructor
    public static class CacheKey {

        private Class targetType;

        private Class sourceType;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey target = ((CacheKey) obj);
            return target.targetType == targetType && target.sourceType == sourceType;
        }

        public int hashCode() {
            int result = this.targetType != null ? this.targetType.hashCode() : 0;
            result = 31 * result + (this.sourceType != null ? this.sourceType.hashCode() : 0);
            return result;
        }
    }
}
