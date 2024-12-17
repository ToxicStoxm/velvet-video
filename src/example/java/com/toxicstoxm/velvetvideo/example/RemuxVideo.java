package com.toxicstoxm.velvet_video_remastered.example;

import com.toxicstoxm.velvet_video_remastered.*;
import com.toxicstoxm.velvet_video_remastered.impl.VelvetVideoLib;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemuxVideo {

	public static void main(String[] args) {
		File src = Util.getFile("https://www.sample-videos.com/video123/mkv/240/big_buck_bunny_240p_10mb.mkv", "source.mkv");
		remuxToMp4(src);
	}

	private static void remuxToMp4(File src) {
		IVelvetVideoLib lib = VelvetVideoLib.getInstance();
		File output = new File(src.getParent(), "remux.mp4");
		try (IDemuxer demuxer = lib.demuxer(src)) {
			List<IDecoderStream<?,?,?>> decoders = demuxer.streams();
			Map<Integer, Integer> decoderToRemuxerIndex = new HashMap<>();

			// Create a muxer with a remuxing stream for every source stream:
			IMuxerBuilder muxerBuilder = lib.muxer("mp4");
			for (int remuxerIndex = 0; remuxerIndex < decoders.size(); remuxerIndex++) {
				IDecoderStream<?, ?, ?> decoder = decoders.get(remuxerIndex);
				muxerBuilder.remuxer(lib.remuxer(decoder));
				decoderToRemuxerIndex.put(decoder.index(), remuxerIndex);
			}

			// Iterate the source file raw packets and send them to appropriate remuxers
			try (IMuxer muxer = muxerBuilder.build(output)) {
				IRawPacket rawPacket = null;
				while((rawPacket = demuxer.nextRawPacket()) != null) {
					Integer remuxIndex = decoderToRemuxerIndex.get(rawPacket.streamIndex());
					if (remuxIndex != null) {
						muxer.remuxer(remuxIndex).writeRaw(rawPacket.bytes());
					}
				}
			}
			System.out.println(output);
		}
	}

}
