package com.toxicstoxm.velvet_video_remastered.impl.middle;

import com.toxicstoxm.velvet_video_remastered.IDecodedPacket;
import com.toxicstoxm.velvet_video_remastered.impl.VelvetVideoLib.DemuxerImpl.AbstractDecoderStream;
import com.toxicstoxm.velvet_video_remastered.impl.jnr.AVFrame;

public interface IFrameHolder extends AutoCloseable {

	default long pts() {
		return frame().pts.get();
	}

	AVFrame frame();

	IDecodedPacket<?> decode(AVFrame frame, AbstractDecoderStream stream);

	@Override
	void close();
}
