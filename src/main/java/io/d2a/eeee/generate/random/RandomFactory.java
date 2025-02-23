package io.d2a.eeee.generate.random;

import io.d2a.eeee.annotation.annotations.Depth;
import io.d2a.eeee.annotation.annotations.Generate;
import io.d2a.eeee.annotation.annotations.Use;
import io.d2a.eeee.annotation.provider.AnnotationProvider;
import io.d2a.eeee.annotation.provider.EmptyAnnotationProvider;
import io.d2a.eeee.annotation.provider.PriorityAnnotationProvider;
import io.d2a.eeee.inject.Inject;
import io.d2a.eeee.inject.Injector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RandomFactory {

    public static final Random RANDOM = new Random();

    /**
     * @param typeClass type of parameter
     * @param provider  annotation provider
     * @return object
     * @throws Exception if anything goes wrong (this is the most useful JavaDoc ever)
     */
    public static <T> T generate(
        final Class<T> typeClass,
        final String constructorName,
        final AnnotationProvider provider,
        final Injector injector,
        final Map<Class<?>, Integer> currentDepth,
        final Map<Class<?>, Integer> maxDepth
    )
        throws Exception {
        final Generator<T> generator = Generators.findGenerator(typeClass);
        if (generator == null) {
            return fromGenerateConstructor(
                typeClass,
                constructorName,
                injector,
                currentDepth,
                maxDepth
            );
        }
        return generator.generate(RANDOM, provider, typeClass);
    }

    public static <T> T generate(
        final Class<T> typeClass,
        final String constructorName,
        final AnnotationProvider provider
    ) throws Exception {
        return generate(
            typeClass,
            constructorName,
            provider,
            null,
            new HashMap<>(),
            new HashMap<>()
        );
    }

    public static <T> T generate(
        final Class<T> typeClass,
        final String constructorName
    ) throws Exception {
        return generate(typeClass, constructorName, EmptyAnnotationProvider.DEFAULT);
    }

    public static <T> T generate(final Class<T> typeClass)
        throws Exception {
        return generate(
            typeClass, ""
        );
    }

    /**
     * The method looks for a constructor in the given class, which was annotated with {@link {@link
     * Generate}.
     *
     * @param clazz Class in which the suitable constructor is searched for
     * @param <T>   Type of class
     * @return Constructor annotated with {@link Generate}
     * @throws NoSuchMethodException if no suitable constructor is found
     */
    public static <T> Constructor<T> findGenerateConstructor(
        final String name,
        final Class<T> clazz
    ) throws NoSuchMethodException {

        for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (!constructor.isAnnotationPresent(Generate.class)) {
                continue;
            }
            final Generate generate = constructor.getAnnotation(Generate.class);
            if (generate.value().equals(name) || (generate.value().equals("") && name == null)) {
                return clazz.getDeclaredConstructor(constructor.getParameterTypes());
            }
        }
        throw new NoSuchMethodException("no generator-constructor not found for " + clazz);
    }

    /**
     * This method creates a new object of the specified class. For this purpose the method searches
     * for a suitable constructor (annotated with {@link Generate}) and generates random values for
     * this constructor.
     *
     * @param clazz The class of the object to be instantiated
     * @param <T>   Type of class
     * @return a new object of (Class@T)
     * @throws Exception The usual Java Reflect Error pile
     */
    public static <T> T fromGenerateConstructor(
        final Class<T> clazz,
        final String constructorName,
        final Injector injector,

        final Map<Class<?>, Integer> currentDepth,
        final Map<Class<?>, Integer> maxDepth
    ) throws Exception {
        final Constructor<T> constructor = findGenerateConstructor(constructorName, clazz);
        final List<Object> parameters = new ArrayList<>();

        for (final Parameter parameter : constructor.getParameters()) {

            // inject value instead of generating?
            if (parameter.isAnnotationPresent(Inject.class)) {
                final Object val;
                if (injector != null) {
                    val = injector.find(
                        parameter.getType(),
                        parameter.getAnnotation(Inject.class).value()
                    );
                } else {
                    val = null;
                }
                parameters.add(val);
                continue;
            }

            // use parameter type generator or specified
            final Class<?> generator;
            if (!parameter.getType().isArray() && parameter.isAnnotationPresent(Use.class)) {
                generator = parameter.getAnnotation(Use.class).value();
            } else {
                generator = parameter.getType();
            }

            // check depth
            if (currentDepth != null && maxDepth != null) {

                // annotation
                final Depth depth = parameter.getAnnotation(Depth.class);
                if (depth != null) {
                    if (!currentDepth.containsKey(generator) && !maxDepth.containsKey(generator)) {
                        currentDepth.put(generator, 0);
                        maxDepth.put(generator, depth.value());
                    }
                }

                final int current = currentDepth.getOrDefault(generator, 0);
                final int max = maxDepth.getOrDefault(generator, Integer.MAX_VALUE);

                if (current >= max) {
                    parameters.add(null);
                    continue;
                }

                currentDepth.put(generator, current + 1);
            }

            final Generate generate = parameter.getAnnotation(Generate.class);

            final Object val = generate(
                generator,
                generate != null ? generate.value() : null,
                new PriorityAnnotationProvider(
                    parameter::getAnnotation,
                    constructor::getAnnotation
                ),
                injector,
                currentDepth,
                maxDepth
            );

            parameters.add(val);
        }
        return constructor.newInstance(parameters.toArray());
    }

    public static <T> T fromGenerateConstructor(
        final Class<T> clazz,
        final String constructorName,
        final Injector injector
    ) throws Exception {
        return fromGenerateConstructor(
            clazz,
            constructorName,
            injector,
            new HashMap<>(),
            new HashMap<>()
        );
    }

    public static <T> T fromGenerateConstructor(
        final Class<T> clazz,
        final String constructorName
    ) throws Exception {
        return fromGenerateConstructor(clazz, constructorName, null);
    }

    public static <T> T fromGenerateConstructor(
        final Class<T> clazz
    ) throws Exception {
        return fromGenerateConstructor(clazz, "", null);
    }

    /**
     * Fills a specified array with random values using the {@link RandomFactory#fromGenerateConstructor(Class,
     * String, Injector, Map, Map)} method.
     *
     * @param array The array which should be filled
     * @param <T>   Type of array
     * @throws Exception The usual Java Reflect Error pile
     */
    @SuppressWarnings("unchecked")
    public static <T> void fillArrayRandom(
        final T[] array,
        final String constructorName,
        final Injector injector
    ) throws Exception {
        final Class<T> clazz = (Class<T>) array.getClass().getComponentType();
        for (int i = 0; i < array.length; i++) {
            array[i] = fromGenerateConstructor(clazz, constructorName, injector);
        }
    }

    public static <T> void fillArrayRandom(
        final T[] array,
        final String constructorName
    ) throws Exception {
        fillArrayRandom(array, constructorName, null);
    }

    public static <T> void fillArrayRandom(final T[] array) throws Exception {
        fillArrayRandom(array, "", null);
    }

}
