package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.velvet_video_remastered.IAudioStreamProperties;
import lombok.Value;
import lombok.experimental.Accessors;

import javax.sound.sampled.AudioFormat;

@Accessors(fluent = true)
@Value
class AudioStreamPropertiesImpl implements IAudioStreamProperties {

	String codec;
	AudioFormat format;
	long nanoduration;
	long samples;

}
