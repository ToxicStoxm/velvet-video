package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.velvet_video_remastered.VelvetVideoException;
import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.provider.ParameterFlags;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class JNRHelper {

	private static final Logger LOG = LoggerFactory.getLogger("velvet-video");
	private static final Map<Class<?>, Object> libCache = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static <L> L load(@NotNull Class<L> clazz, String libShortName, int libVersion) {
		return clazz.cast(libCache.computeIfAbsent(clazz, (Class<?> cl) -> JNRHelper.forceLoad((Class<L>) cl, libShortName, libVersion)));
	}

	private static <L> L forceLoad(Class<L> clazz, String libShortName, int libVersion) {
		try {
			LOG.atDebug().addArgument(libShortName).addArgument(libVersion).log("Requesting loading native lib {}.{}");
			System.out.println("Loading native library: " + libShortName);
			System.loadLibrary(libShortName); // Load system library
			System.out.println("Native library loaded: " + libShortName);
			LibraryLoader<L> loader = LibraryLoader.create(clazz);
			System.out.println("LibraryLoader created for: " + clazz.getName());
			loader.failImmediately();
			System.out.println("LibraryLoader configured to fail immediately.");
			L lib = loader.load(libShortName);
			System.out.println("Library successfully loaded: " + libShortName);
			LOG.atDebug().addArgument(libShortName).log("Loaded {}");
			return lib;
		} catch (UnsatisfiedLinkError e) {
            LOG.error("Error loading native library {}", libShortName, e);
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
			LOG.atDebug().addArgument(libShortName).log("Preloaded native library: {}");
			return 0;
		} catch (LinkageError e) {
			LOG.atError().addArgument(libShortName).addArgument(e.getMessage())
					.log("Error preloading native library {} : {}");
			return -1;
		}
	}
}
