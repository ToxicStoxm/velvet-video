package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.velvet_video_remastered.IDecoderStream;
import com.toxicstoxm.velvet_video_remastered.IRemuxerBuilder;

public class RemuxerBuilderImpl implements IRemuxerBuilder {

	final IDecoderStream<?, ?, ?> decoder;

	Integer timebaseNum;
	Integer timebaseDen;

	public RemuxerBuilderImpl(IDecoderStream<?, ?, ?> decoder) {
		this.decoder = decoder;
	}

	@Override
	public RemuxerBuilderImpl framerate(int framerate) {
		this.timebaseNum = 1;
		this.timebaseDen = framerate;
		return this;
	}

	@Override
	public RemuxerBuilderImpl framerate(int num, int den) {
		this.timebaseNum = num;
		this.timebaseDen = den;
		return this;
	}

}
