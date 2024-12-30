package com.toxicstoxm.velvet_video_remastered.tools;

import org.jetbrains.annotations.NotNull;

public class MinFinder {

    public static <T> @NotNull IMinimax<T> find(@NotNull Iterable<T> collection, IFunction<T, Float> metrics) {
        float value = Float.POSITIVE_INFINITY;
        T entry = null;
        for(T e : collection) {
            Float val = metrics.get(e);
            if (val < value) {
                value = val;
                entry = e;
            }
        }
        final T entry2 = entry;
        final float value2 = value;
        return new IMinimax<T>() {

            @Override
            public T get() {
                return entry2;
            }

            @Override
            public float value() {
                return value2;
            }
        };

    }

}