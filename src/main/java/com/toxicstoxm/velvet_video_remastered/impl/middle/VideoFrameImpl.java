package com.toxicstoxm.velvet_video_remastered.impl.middle;

import com.toxicstoxm.velvet_video_remastered.IVideoDecoderStream;
import com.toxicstoxm.velvet_video_remastered.IVideoFrame;
import lombok.Value;
import lombok.experimental.Accessors;

import java.awt.image.BufferedImage;

@Accessors(fluent = true)
@Value
class VideoFrameImpl implements IVideoFrame {
    BufferedImage image;
    long nanostamp;
    long nanoduration;
    IVideoDecoderStream stream;

    @Override
	public String toString() {
    	return "Video frame t=" + nanostamp + " stream:" + stream.name();
    }
}