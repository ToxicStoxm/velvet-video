package com.toxicstoxm.velvet_video_remastered.tools;

public class MinFinder {

    public static <T> IMinimax<T> find(Iterable<T> collection, IFunction<T, Float> metrics) {
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