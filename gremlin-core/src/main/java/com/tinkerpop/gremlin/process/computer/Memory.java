package com.tinkerpop.gremlin.process.computer;

import org.javatuples.Pair;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Memory of a {@link GraphComputer} is a global data structure where by vertices can communicate information with one another.
 * Moreover, it also contains global information about the state of the computation such as runtime and the current iteration.
 * The Memory data is logically updated in parallel using associative/commutative methods which have embarrassingly parallel implementations.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Memory {

    /**
     * Whether the key exists in the memory.
     *
     * @param key key to search the memory for.
     * @return whether the key exists
     */
    public default boolean exists(final String key) {
        return this.keys().contains(key);
    }

    /**
     * The set of keys currently associated with this memory.
     *
     * @return the memory's key set.
     */
    public Set<String> keys();

    /**
     * Get the value associated with the provided key.
     *
     * @param key the key of the value
     * @param <R> the type of the value
     * @return the value
     * @throws IllegalArgumentException is thrown if the key does not exist
     */
    public <R> R get(final String key) throws IllegalArgumentException;

    /**
     * Set the value of the provided key. This is typically called in setup() and/or terminate() of the {@link VertexProgram}.
     * If this is called during execute(), there is no guarantee as to the ultimately stored value as call order is indeterminate.
     *
     * @param key   they key to set a value for
     * @param value the value to set for the key
     */
    public void set(final String key, Object value);

    /**
     * A helper method that generates a {@link Map} of the memory key/values.
     *
     * @return the map representation of the memory key/values
     */
    public default Map<String, Object> asMap() {
        final Map<String, Object> map = keys().stream()
                .filter(this::exists)
                .map(key -> Pair.with(key, get(key)))
                .collect(Collectors.toMap(kv -> kv.getValue0(), Pair::getValue1));
        return Collections.unmodifiableMap(map);
    }

    /**
     * Get the current iteration number.
     *
     * @return the current iteration
     */
    public int getIteration();

    /**
     * Get the amount of milliseconds the {@link GraphComputer} has been executing thus far.
     *
     * @return the total time in milliseconds
     */
    public long getRuntime();

    /**
     * Add the provided delta value to the long value currently stored at the key.
     *
     * @param key   the key of the long value
     * @param delta the adjusting amount (can be negative for decrement)
     * @return the adjusted value (according to the previous iterations current value + delta)
     */
    public long incr(final String key, final long delta);

    /**
     * Logically AND the provided boolean value with the boolean value currently stored at the key.
     *
     * @param key  the key of the boolean value
     * @param bool the boolean to AND
     * @return the adjusted value (according to the previous iterations current value && bool)
     */
    public boolean and(final String key, final boolean bool);

    /**
     * Logically OR the provided boolean value with the boolean value currently stored at the key.
     *
     * @param key  the key of the boolean value
     * @param bool the boolean to OR
     * @return the adjusted value (according to the previous iterations current value || bool)
     */
    public boolean or(final String key, final boolean bool);

    /**
     * A helper method that states whether the current iteration is 0.
     *
     * @return whether this is the first iteration
     */
    public default boolean isInitialIteration() {
        return this.getIteration() == 0;
    }

    /**
     * The Admin interface is used by the {@link GraphComputer} to update the Memory.
     * The developer should never need to type-cast the provided Memory to Memory.Admin.
     */
    public interface Admin extends Memory {

        public void incrIteration();

        public void setRuntime(final long runtime);
    }


    public static class Exceptions {

        public static IllegalArgumentException memoryKeyCanNotBeEmpty() {
            return new IllegalArgumentException("Graph computer memory key can not be the empty string");
        }

        public static IllegalArgumentException memoryKeyCanNotBeNull() {
            return new IllegalArgumentException("Graph computer memory key can not be null");
        }

        public static IllegalArgumentException memoryValueCanNotBeNull() {
            return new IllegalArgumentException("Graph computer memory value can not be null");
        }

        public static IllegalStateException memoryCompleteAndImmutable() {
            return new IllegalStateException("Graph computer memory is complete and immutable");
        }

        public static IllegalArgumentException memoryDoesNotExist(final String key) {
            return new IllegalArgumentException("The memory does not have a value for provided key: " + key);
        }

        public static UnsupportedOperationException dataTypeOfMemoryValueNotSupported(final Object val) {
            return new UnsupportedOperationException(String.format("Graph computer memory value [%s] is of type %s is not supported", val, val.getClass()));
        }
    }

}
