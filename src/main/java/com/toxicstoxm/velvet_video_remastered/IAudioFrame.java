package com.toxicstoxm.velvet_video_remastered;

/**
 * Frame of audio data.
 */
public interface IAudioFrame extends IDecodedPacket<IAudioDecoderStream> {

	/**
	 * Decoded audio samples.
	 * @return audio samples bytes
	 */
	byte[] samples();

	@Override
	default MediaType type() {
		return MediaType.Audio;
	}

	@Override
	default IAudioFrame asAudio() {
		return this;
	}
}