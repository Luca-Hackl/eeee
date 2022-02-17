package io.d2a.eeee.generate.placeholder;

import io.d2a.eeee.annotations.AnnotationProvider;
import io.d2a.eeee.annotations.AnyAnnotationProvider;
import io.d2a.eeee.annotations.generate.Generate;
import io.d2a.eeee.annotations.generate.Use;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomFactory {

    public static final Random RANDOM = new Random();

    /**
     * @param typeClass type of parameter
     * @param provider  annotation provider
     * @return object
     * @throws Exception if anything goes wrong (this is the most useful JavaDoc ever)
     */
    private static Object generateRandomValue(
        final Class<?> typeClass,
        final AnnotationProvider provider
    )
        throws Exception {

        Generator<?> generator = Generators.GENERATORS.get(typeClass);
        if (generator == null) {
            // check if class implements the generator interface
            if (Generator.class.isAssignableFrom(typeClass)) {
                // create new object and save to generators for later use
                final Constructor<?> constructor = typeClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                generator = (Generator<?>) constructor.newInstance();
                Generators.GENERATORS.put(typeClass, generator);
            } else {
                // check if the type can be generated by using the constructor
                return createRandom(typeClass);
            }
        }

        return generator.generate(RANDOM, provider);
    }

    private static <T> Constructor<T> findGenerateConstructor(final Class<T> clazz)
        throws Exception {

        for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Generate.class)) {
                return clazz.getDeclaredConstructor(constructor.getParameterTypes());
            }
        }
        throw new NoSuchMethodException("no generator-constructor not found for " + clazz);
    }

    public static <T> T createRandom(final Class<T> clazz) throws Exception {
        final Constructor<T> constructor = findGenerateConstructor(clazz);
        final List<Object> parameters = new ArrayList<>();
        for (final Parameter parameter : constructor.getParameters()) {

            // use parameter type generator or specified
            final Class<?> generator;
            if (parameter.isAnnotationPresent(Use.class)) {
                generator = parameter.getAnnotation(Use.class).value();
            } else {
                generator = parameter.getType();
            }

            final Object val = generateRandomValue(
                generator,
                new AnyAnnotationProvider(
                    parameter::getAnnotation,
                    constructor::getAnnotation
                ));

            parameters.add(val);
        }
        return constructor.newInstance(parameters.toArray());
    }

    public static <T> void fillArrayRandom(final T[] array, final Class<T> clazz) throws Exception {
        for (int i = 0; i < array.length; i++) {
            array[i] = createRandom(clazz);
        }
    }

}
