package com.toxicstoxm.velvet_video_remastered.example;

import com.toxicstoxm.velvet_video_remastered.*;
import com.toxicstoxm.velvet_video_remastered.impl.VelvetVideoLib;

import java.awt.image.BufferedImage;
import java.io.File;

public class TranscodeVideoWithTimingEffects {

	public static void main(String[] args) {
		File src = Util.getFile("https://www.sample-videos.com/video123/mkv/240/big_buck_bunny_240p_10mb.mkv", "source.mkv");
		transcodeToVp9WithSloMo(src);
	}

	private static void transcodeToVp9WithSloMo(File src) {
		IVelvetVideoLib lib = VelvetVideoLib.getInstance();
		try (IDemuxer demuxer = lib.demuxer(src)) {
			IVideoDecoderStream videoDecoderStream = demuxer.videoStreams().get(0);
			File output = new File(src.getParent(), "transcodevp9.webm");
			System.out.println(output);
			double origFramerate = videoDecoderStream.properties().framerate();
			// 1/4 framerate
			int newFramerateNum = 4;
			int newFramerateDen = (int) (origFramerate);
			IVideoEncoderBuilder encoderBuilder = lib.videoEncoder("libvpx-vp9")
				.framerate(newFramerateNum, newFramerateDen)
				.bitrate(1000000)
				.dimensions(videoDecoderStream.properties().width(), videoDecoderStream.properties().height());
			try (IMuxer muxer = lib.muxer("webm").videoEncoder(encoderBuilder).build(output)) {
				IVideoEncoderStream videoEncoder = muxer.videoEncoder(0);
				for (IVideoFrame videoFrame : videoDecoderStream) {
					BufferedImage image = videoFrame.image();
					videoEncoder.encode(image);
				}
			}
			System.out.println(output);
		}
	}

}
