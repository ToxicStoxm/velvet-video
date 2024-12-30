package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.velvet_video_remastered.VelvetVideoException;
import com.toxicstoxm.velvet_video_remastered.tools.logging.VelvetVideoLogAreaBundle;
import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.provider.ParameterFlags;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class JNRHelper {
	private static final Map<Class<?>, Object> libCache = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static <L> L load(@NotNull Class<L> clazz, String libShortName, int libVersion) {
		return clazz.cast(libCache.computeIfAbsent(clazz, (Class<?> cl) -> JNRHelper.forceLoad((Class<L>) cl, libShortName, libVersion)));
	}

	private static <L> L forceLoad(Class<L> clazz, String libShortName, int libVersion) {
		try {
			VelvetVideoLib.getLogger().debug("Requesting loading native lib " + libShortName + "." + libVersion, new VelvetVideoLogAreaBundle.VelvetVideo());
			VelvetVideoLib.getLogger().debug("Loading native library: " + libShortName, new VelvetVideoLogAreaBundle.VelvetVideo());
			System.loadLibrary(libShortName); // Load system library
			VelvetVideoLib.getLogger().debug("Native library loaded: " + libShortName, new VelvetVideoLogAreaBundle.VelvetVideo());
			LibraryLoader<L> loader = LibraryLoader.create(clazz);
			VelvetVideoLib.getLogger().debug("LibraryLoader created for: " + clazz.getName(), new VelvetVideoLogAreaBundle.VelvetVideo());
			loader.failImmediately();
			VelvetVideoLib.getLogger().debug("LibraryLoader configured to fail immediately.", new VelvetVideoLogAreaBundle.VelvetVideo());
			L lib = loader.load(libShortName);
			VelvetVideoLib.getLogger().debug("Library successfully loaded: " + libShortName, new VelvetVideoLogAreaBundle.VelvetVideo());
			VelvetVideoLib.getLogger().debug("Loaded " + libShortName, new VelvetVideoLogAreaBundle.VelvetVideo());
			return lib;
		} catch (UnsatisfiedLinkError e) {
			VelvetVideoLib.getLogger().error("Error loading native library " + libShortName + ". Error message: \"" + e.getMessage() + "\"", new VelvetVideoLogAreaBundle.VelvetVideo());
			throw new VelvetVideoException("Error loading native library " + libShortName, e);
		}
	}

	public static <T extends Struct> @NotNull T struct(@NotNull Class<T> clazz, @NotNull Pointer value) {
		try {
			Constructor<T> constructor = clazz.getConstructor(Runtime.class);
			T instance = constructor.newInstance(value.getRuntime());
			instance.useMemory(value);
			T.getMemory(instance, ParameterFlags.OUT);
			return instance;
		} catch (ReflectiveOperationException e) {
			throw new VelvetVideoException(e);
		}
	}

	public static <T extends Struct> @NotNull T struct(Class<T> clazz, @NotNull PointerByReference pp) {
		return struct(clazz, pp.getValue());
	}

	public static Pointer ptr(Struct.@NotNull NumberField member) {
		return member.getMemory().slice(member.offset());
	}

	public static int preload(String libShortName, int libVersion) {
		try {
			System.loadLibrary(libShortName);
			VelvetVideoLib.getLogger().debug("Preloaded native library: " + libShortName, new VelvetVideoLogAreaBundle.VelvetVideo());
			return 0;
		} catch (LinkageError e) {
			VelvetVideoLib.getLogger().error("Error preloading native library " + libShortName + " : " + e.getMessage());
			return -1;
		}
	}
}
