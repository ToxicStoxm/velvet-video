package com.toxicstoxm.velvet_video_remastered.impl.middle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Feeder {

	public static <I, O> @Nullable O next(@NotNull Supplier<I> source, @NotNull Function<I, O> processor) {
		for(;;) {
			I input = source.get();
			O result = processor.apply(input);
			if (result != null)
				return result;
			if (input == null) {
				return null;
			}
		}
	}

	public static <I, O> void feed(I input, @NotNull Function<I, O> processor, Consumer<O> output) {

		for(;;) {
			O result = processor.apply(input);
			if (result != null) {
				output.accept(result);
				if (input == null)
					continue;
			}
			if (result == null && input == null)
				output.accept(null);
			return;
		}
	}
}
