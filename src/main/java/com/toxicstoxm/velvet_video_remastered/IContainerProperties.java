package com.toxicstoxm.velvet_video_remastered;

/**
 * Media container properties.
 */
public interface IContainerProperties {

	/**
	 * @return format name
	 */
    String format();

    /**
     * @return total duration, in nanoseconds
     */
	long nanoduration();
}