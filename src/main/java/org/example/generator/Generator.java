package org.example.generator;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Generator {
    private static final int MAX_DEPTH = 4;
    private static final int MAX_ATTEMPTS = 100;
    private final Random random;

    private static class GenerationFailure extends IllegalArgumentException {
        GenerationFailure(String message) {
            super(message);
        }

        GenerationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public Generator() {
        random = new Random();
    }

    public Generator(long seed) {
        random = new Random(seed);
    }

    public Object generateValueOfType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type must not be null");
        }
        return generate(type, 0);
    }

    private Object generate(Type type, int depth) {
        if (depth > MAX_DEPTH) {
            throw new GenerationFailure("Maximum nesting depth exceeded for " + type);
        }

        // only List<T> and Map<K, V> supported parameterized types
        if (type instanceof ParameterizedType generic) {
            Type[] arguments = generic.getActualTypeArguments();
            if (generic.getRawType() == List.class) {
                return makeList(arguments[0], depth);
            }
            if (generic.getRawType() == Map.class) {
                return makeMap(arguments[0], arguments[1], depth);
            }
            throw new GenerationFailure("Unsupported generic type: " + type);
        }
        if (!(type instanceof Class<?> clazz)) {
            throw new GenerationFailure("Unsupported type: " + type);
        }

        if (clazz == int.class || clazz == Integer.class) return random.nextInt(100) + 1;
        if (clazz == long.class || clazz == Long.class) return (long) random.nextInt(100) + 1;
        if (clazz == short.class || clazz == Short.class) return (short) (random.nextInt(100) + 1);
        if (clazz == byte.class || clazz == Byte.class) return (byte) (random.nextInt(100) + 1);
        if (clazz == double.class || clazz == Double.class) return 1 + random.nextDouble() * 99;
        if (clazz == float.class || clazz == Float.class) return 1 + random.nextFloat() * 99;
        if (clazz == boolean.class || clazz == Boolean.class) return random.nextBoolean();
        if (clazz == char.class || clazz == Character.class) return (char) ('a' + random.nextInt(26));
        if (clazz == String.class) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(8) + 3;
            for (int i = 0; i < length; i++) {
                text.append((char) ('a' + random.nextInt(26)));
            }
            return text.toString();
        }
        if (clazz.isEnum()) {
            Object[] values = clazz.getEnumConstants();
            if (values.length == 0) {
                throw new GenerationFailure("Empty enum: " + clazz.getName());
            }
            return values[random.nextInt(values.length)];
        }
        if (clazz.isArray()) {
            int size = random.nextInt(3) + 1;
            Class<?> elementType = clazz.getComponentType();
            Object array = Array.newInstance(elementType, size);
            for (int i = 0; i < size; i++) {
                Array.set(array, i, generate(elementType, depth + 1));
            }
            return array;
        }
        if (clazz == List.class) return makeList(String.class, depth);
        if (clazz == Map.class) return makeMap(String.class, Integer.class, depth);

        if (clazz == void.class || clazz == Void.class) {
            throw new GenerationFailure("Unsupported type: " + clazz.getName());
        }
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            Generatable annotation = clazz.getDeclaredAnnotation(Generatable.class);
            if (annotation == null) {
                throw new GenerationFailure("Missing @Generatable on " + clazz.getName());
            }
            Class<?>[] implementations = annotation.implementations();
            if (implementations.length == 0) {
                throw new GenerationFailure("No implementations for " + clazz.getName());
            }
            for (Class<?> implementation : implementations) {
                if (!clazz.isAssignableFrom(implementation)
                        || implementation.isInterface()
                        || Modifier.isAbstract(implementation.getModifiers())) {
                    throw new GenerationFailure("Invalid implementation " + implementation.getName()
                            + " for " + clazz.getName());
                }
            }
            return generate(implementations[random.nextInt(implementations.length)], depth + 1);
        }
        return makeObject(clazz, depth);
    }

    private List<Object> makeList(Type elementType, int depth) {
        List<Object> result = new ArrayList<>();
        int size = random.nextInt(3) + 1;
        for (int i = 0; i < size; i++) {
            result.add(generate(elementType, depth + 1));
        }
        return result;
    }

    private Map<Object, Object> makeMap(Type keyType, Type valueType, int depth) {
        Map<Object, Object> result = new LinkedHashMap<>();
        int size = random.nextInt(3) + 1;
        for (int i = 0; i < size; i++) {
            result.put(generate(keyType, depth + 1), generate(valueType, depth + 1));
        }
        return result;
    }

    private Object makeObject(Class<?> clazz, int depth) {
        Constructor<?>[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            throw new GenerationFailure("No public constructor for " + clazz.getName());
        }
        Arrays.sort(constructors, Comparator
                .<Constructor<?>>comparingInt(Constructor::getParameterCount)
                .reversed()
                .thenComparing(Constructor::toString));

        GenerationFailure failure = new GenerationFailure("No usable public constructor for " + clazz.getName());
        for (Constructor<?> constructor : constructors) {
            try {
                return tryConstructor(constructor, depth);
            } catch (GenerationFailure e) {
                if (constructors.length == 1) {
                    throw e;
                }
                failure.addSuppressed(new GenerationFailure("Could not use " + constructor, e));
            }
        }
        throw failure;
    }

    private Object tryConstructor(Constructor<?> constructor, int depth) {
        Class<?> clazz = constructor.getDeclaringClass();
        Parameter[] parameters = constructor.getParameters();
        Throwable lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Generatable rule = parameter.getAnnotation(Generatable.class);
                if (rule != null && rule.nullable() && parameter.getType().isPrimitive()) {
                    throw new GenerationFailure("Primitive parameter cannot be nullable: " + parameter);
                }
                if (rule != null && rule.nullable() && depth >= MAX_DEPTH - 1) {
                    arguments[i] = null;
                } else {
                    arguments[i] = generate(parameter.getParameterizedType(), depth + 1);
                }
            }
            try {
                return constructor.newInstance(arguments);
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IllegalArgumentException)) {
                    throw new IllegalArgumentException("Constructor failed for " + clazz.getName(), e.getCause());
                }
                // The constructor rejected these values. Try a new set.
                lastFailure = e.getCause();
            } catch (ReflectiveOperationException e) {
                throw new GenerationFailure("Cannot create " + clazz.getName(), e);
            }
        }
        throw new GenerationFailure("No valid arguments after " + MAX_ATTEMPTS
                + " attempts for " + clazz.getName(), lastFailure);
    }
}
