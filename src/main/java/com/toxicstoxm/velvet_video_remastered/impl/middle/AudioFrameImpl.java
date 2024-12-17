package com.toxicstoxm.velvet_video_remastered.impl.middle;

import com.toxicstoxm.velvet_video_remastered.IAudioDecoderStream;
import com.toxicstoxm.velvet_video_remastered.IAudioFrame;
import lombok.Value;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Value
public class AudioFrameImpl implements IAudioFrame {

	byte[] samples;
	long nanostamp;
    long nanoduration;
    IAudioDecoderStream stream;

    @Override
	public String toString() {
    	return "Audio frame t=" + nanostamp + " stream:" + stream.name();
    }
}