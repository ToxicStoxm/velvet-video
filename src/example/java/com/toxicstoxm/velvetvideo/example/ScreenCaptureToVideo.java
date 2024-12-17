package com.toxicstoxm.velvet_video_remastered.example;

import com.toxicstoxm.velvet_video_remastered.IMuxer;
import com.toxicstoxm.velvet_video_remastered.IVelvetVideoLib;
import com.toxicstoxm.velvet_video_remastered.IVideoEncoderBuilder;
import com.toxicstoxm.velvet_video_remastered.IVideoEncoderStream;
import com.toxicstoxm.velvet_video_remastered.impl.VelvetVideoLib;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class ScreenCaptureToVideo {

	private static final int FRAMERATE = 25;

	public static void main(String[] args) throws AWTException {
		File dest = new File(Util.workDir(), "screenCapture.mp4");
		screenCapture(dest, 250);
	}

	private static void screenCapture(File dest, int frames) throws AWTException {

		Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		Robot robot = new Robot();
		IVelvetVideoLib lib = VelvetVideoLib.getInstance();

		IVideoEncoderBuilder encoderBuilder = lib.videoEncoder("libx264")
			.framerate(FRAMERATE)
			.dimensions(screenRect.width, screenRect.height)
			.bitrate(1000000);

		try (IMuxer muxer = lib.muxer("mp4").videoEncoder(encoderBuilder).build(dest)) {
			IVideoEncoderStream videoEncoder = muxer.videoEncoder(0);
			for (int i = 0; i < frames; i++) {
				BufferedImage image = robot.createScreenCapture(screenRect);
				videoEncoder.encode(image);
			}
		}
		System.out.println(dest);
	}

}
