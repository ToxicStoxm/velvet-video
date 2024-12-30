package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.velvet_video_remastered.IAudioEncoderBuilder;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;

class AudioEncoderBuilderImpl extends AbstractEncoderBuilderImpl<IAudioEncoderBuilder> implements IAudioEncoderBuilder {

	AudioFormat inputFormat;

	public AudioEncoderBuilderImpl(String codec, @NotNull AudioFormat inputFormat) {
		super(codec);
		this.inputFormat = inputFormat;
		framerate((int)inputFormat.getSampleRate());
	}

}