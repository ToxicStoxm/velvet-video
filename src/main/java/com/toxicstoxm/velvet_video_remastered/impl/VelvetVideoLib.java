package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.YAJL.Logger;
import com.toxicstoxm.velvet_video_remastered.*;
import com.toxicstoxm.velvet_video_remastered.impl.VelvetVideoLib.DemuxerImpl.AbstractDecoderStream;
import com.toxicstoxm.velvet_video_remastered.impl.jnr.*;
import com.toxicstoxm.velvet_video_remastered.impl.jnr.LibAVFormat.ICustomAvioCallback;
import com.toxicstoxm.velvet_video_remastered.impl.middle.*;
import com.toxicstoxm.velvet_video_remastered.tools.logging.VelvetVideoLogAreaBundle;
import jnr.ffi.Pointer;
import jnr.ffi.Struct;
import jnr.ffi.byref.PointerByReference;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class VelvetVideoLib implements IVelvetVideoLib {

    private static final int AVIO_CUSTOM_BUFFER_SIZE = 32768;

    private static final long AVNOPTS_VALUE = LibAVUtil.AVNOPTS_VALUE;

	@Getter
	private static Logger logger;

	private static boolean init = false;

    private final LibAVUtil libavutil = JNRHelper.load(LibAVUtil.class, Libraries.avutil, Libraries.avutil_version);
    @SuppressWarnings("unused")
	private final LibSwResample dummyswresample = JNRHelper.load(LibSwResample.class, Libraries.swresample, Libraries.swresample_version);
	@SuppressWarnings("unused")
	private final int dummyopenh264 = JNRHelper.preload(Libraries.openh264, Libraries.openh264_version);

    private final LibAVCodec libavcodec = JNRHelper.load(LibAVCodec.class, Libraries.avcodec, Libraries.avcodec_version);
    private final LibAVFormat libavformat = JNRHelper.load(LibAVFormat.class, Libraries.avformat, Libraries.avformat_version);

    private static volatile IVelvetVideoLib instance;

	public static void initialize(@Nullable Logger logger) {
		if (VelvetVideoLib.init) return;
		VelvetVideoLib.logger = logger == null ?
				_ -> {} :
				logger;
		VelvetVideoLib.init = true;
	}

	public static IVelvetVideoLib getInstance() {
		if (!init) VelvetVideoLib.initialize(null);
		if (instance == null) {
			synchronized (VelvetVideoLib.class) {
				if (instance == null) {
					instance = new VelvetVideoLib();
				}
			}
		}
		return instance;
	}

    private VelvetVideoLib() {
    }

    private int checkcode(int code) {
    	return libavutil.checkcode(code);
    }

    @Override
    public List<String> codecs(Direction dir, MediaType mediaType) {
    	return libavcodec.codecs(dir, mediaType);
    }

    @Override
    public List<String> formats(Direction dir) {
    	return libavformat.formats(dir);
    }

    @Override
    public IVideoEncoderBuilder videoEncoder(String codec) {
        return new VideoEncoderBuilderImpl(codec);
    }

    @Override
    public IAudioEncoderBuilder audioEncoder(String codec, AudioFormat audioFormat) {
    	return new AudioEncoderBuilderImpl(codec, audioFormat);
    }

    @Override
    public IRemuxerBuilder remuxer(IDecoderStream<?, ?, ?> decoder) {
    	return new RemuxerBuilderImpl(decoder);
    }

    private @NotNull String defaultName(@NotNull AVStream avstream, int index) {
        AVDictionaryEntry entry = libavutil.av_dict_get(avstream.metadata.get(), "handler_name", null, 0);
        if (entry != null) {
            String name = entry.value.get();
            if (!name.equals("VideoHandler")) {
                return name;
            }
        }
        return "video" + index;
    }

    private void initCustomAvio(boolean read, @NotNull AVFormatContext formatCtx, ICustomAvioCallback callback) {
        Pointer buffer = libavutil.av_malloc(AVIO_CUSTOM_BUFFER_SIZE + 64);
        AVIOContext avioCtx = libavformat.avio_alloc_context(buffer, AVIO_CUSTOM_BUFFER_SIZE, read ? 0 : 1, null, read ? callback : null, read ? null : callback, callback);
        int flagz = formatCtx.ctx_flags.get();
        formatCtx.ctx_flags.set(LibAVFormat.AVFMT_FLAG_CUSTOM_IO | flagz);
        formatCtx.pb.set(avioCtx);
    }

    private @NotNull AVFormatContext createMuxerFormatContext(String format, Map<String, String> metadata) {
        AVOutputFormat outputFmt = libavformat.av_guess_format(format, null, null);
        if (outputFmt == null) {
            throw new VelvetVideoException("Unsupported format: " + format);
        }
        PointerByReference ctxptr = new PointerByReference();
        checkcode(libavformat.avformat_alloc_output_context2(ctxptr, outputFmt, null, null));
        AVFormatContext ctx = JNRHelper.struct(AVFormatContext.class, ctxptr.getValue());
        Pointer dictionary = libavutil.createDictionary(metadata);
        ctx.metadata.set(dictionary);
        return ctx;
    }

    private abstract class AbstractMuxerStreamImpl implements AutoCloseable {
		final Consumer<AVPacket> output;
		final AVPacket packet;
	    AVStream stream;
		int codecTimeBaseNum;
		int codecTimeBaseDen;
		int streamIndex;
		long nextPts = 0;

		/** Measured in stream's timebase */
		int defaultFrameDuration;

		AbstractMuxerStreamImpl(Consumer<AVPacket> output) {
			this.output = output;
			this.packet = libavcodec.av_packet_alloc();
		}

		public void init() {
			this.defaultFrameDuration = codecTimeBaseNum * stream.time_base.den.get() / codecTimeBaseDen / stream.time_base.num.get();
			this.streamIndex = stream.index.get();
			logger.info("stream " + stream.index.get() + ": " +
					"timebase " + stream.time_base.num.get() + "/" + stream.time_base.den.get() + ", " +
					"codec [" + stream.codec.get().codec.get().name.get() + "] " +
					"timebase " + codecTimeBaseNum + "/" + codecTimeBaseDen,
					new VelvetVideoLogAreaBundle.Encoder()
			);
		}

		@Override
		public void close() {
			libavcodec.av_packet_unref(packet);
			libavcodec.av_packet_free(new Pointer[] {Struct.getMemory(packet)});
		}

	}

    private class RemuxerStreamImpl extends AbstractMuxerStreamImpl implements IRemuxerStream {

        private int frameSize;

		public RemuxerStreamImpl(@NotNull RemuxerBuilderImpl builder, AVFormatContext formatCtx, Consumer<AVPacket> output) {
			super(output);
    		this.stream = libavformat.avformat_new_stream(formatCtx, null);
			AbstractDecoderStream decoderImpl = (AbstractDecoderStream) builder.decoder;
			checkcode(libavcodec.avcodec_parameters_copy(stream.codecpar.get(), decoderImpl.avstream.codecpar.get()));
        	stream.codecpar.get().codec_tag.set(0);
        	AVCodecContext codecCtx = stream.codec.get();
        	if ((formatCtx.oformat.get().flags.get() & LibAVFormat.AVFMT_GLOBALHEADER) != 0 && decoderImpl.avstream.codecpar.get().codec_id.get() != 27) { // "libx265"
				codecCtx.flags.set(codecCtx.flags.get() | LibAVCodec.CODEC_FLAG_GLOBAL_HEADER);
            }
        	int timeBaseNum = builder.timebaseNum == null ? decoderImpl.codecCtx.time_base.num.get() * decoderImpl.codecCtx.ticks_per_frame.get(): builder.timebaseNum;
        	int timeBaseDen = builder.timebaseDen == null ? decoderImpl.codecCtx.time_base.den.get() : builder.timebaseDen;
        	stream.time_base.num.set(timeBaseNum);
            stream.time_base.den.set(timeBaseDen);
            this.codecTimeBaseNum = timeBaseNum;
            this.codecTimeBaseDen = timeBaseDen;
            decoderImpl.avstream.time_base.num.get();
            decoderImpl.avstream.time_base.den.get();
            if (decoderImpl.codecCtx.codec_type.get() == LibAVCodec.AVMEDIA_TYPE_AUDIO) {
            	this.frameSize = decoderImpl.codecCtx.frame_size.get();
            }
            this.codecTimeBaseDen = timeBaseDen;
		}

		@Override
		public void init() {
			if (frameSize > 0) {
				this.defaultFrameDuration = frameSize * codecTimeBaseDen * stream.time_base.num.get() / codecTimeBaseNum / stream.time_base.den.get();
			} else {
				this.defaultFrameDuration = codecTimeBaseNum * stream.time_base.den.get() / codecTimeBaseDen / stream.time_base.num.get();
			}
			this.streamIndex = stream.index.get();

			logger.info("stream " + stream.index.get() + ": " +
					"timebase " + stream.time_base.num.get() + "/" + stream.time_base.den.get() + ", " +
					"codec [" + stream.codec.get().codec.get().name.get() + "] " +
					"timebase " + codecTimeBaseNum + "/" + codecTimeBaseDen,
					new VelvetVideoLogAreaBundle.Encoder()
			);
		}

    	@Override
		public void writeRaw(byte @NotNull [] packetData) {
			checkcode(libavcodec.av_new_packet(packet, packetData.length));
            packet.data.get().put(0, packetData, 0, packetData.length);
            packet.stream_index.set(streamIndex);
            packet.pts.set(nextPts);
			packet.duration.set(defaultFrameDuration);
			nextPts += defaultFrameDuration;
            output.accept(packet);
            libavcodec.av_packet_unref(packet);
		}
    }

    private abstract class AbstractEncoderStreamImpl<B extends AbstractEncoderBuilderImpl<?>> extends AbstractMuxerStreamImpl {

        protected final AVCodecContext codecCtx;
        protected boolean codecOpened;
        protected final AVCodec codec;
        protected final Pointer codecOpts;

		protected final String filterString;
		protected Filters filters;
		private long nextExpectedPts;

        public AbstractEncoderStreamImpl(@NotNull B builder, AVFormatContext formatCtx, Consumer<AVPacket> output) throws VelvetVideoException {
        	super(output);
			this.codecOpts = libavutil.createDictionary(builder.params);
			this.filterString = builder.filter;
            this.codec = libavcodec.avcodec_find_encoder_by_name(builder.codec);
            if (this.codec == null && builder.decoder == null) {
                throw new VelvetVideoException("Unknown video codec: " + builder.codec);
            }
            this.stream = libavformat.avformat_new_stream(formatCtx, codec);

            this.codecCtx = libavcodec.avcodec_alloc_context3(codec);
			if (codec != null) {
				if ((formatCtx.oformat.get().flags.get() & LibAVFormat.AVFMT_GLOBALHEADER) != 0 && !codec.name.get().equals("libx265")) {
					codecCtx.flags.set(codecCtx.flags.get() | LibAVCodec.CODEC_FLAG_GLOBAL_HEADER);
				}
				codecCtx.codec_id.set(codec.id.get());
				codecCtx.codec_type.set(codec.type.get());
				codecCtx.bit_rate.set(builder.bitrate == null ? 400000 : builder.bitrate);
				codecCtx.time_base.num.set(builder.timebaseNum == null ? 1 : builder.timebaseNum);
				codecCtx.time_base.den.set(builder.timebaseDen == null ? 30 : builder.timebaseDen);

				if (builder.enableExperimental) {
					codecCtx.strict_std_compliance.set(-2);
					formatCtx.strict_std_compliance.set(-2);
				}

				initCodecCtx(builder);

				Pointer dictionary = libavutil.createDictionary(builder.metadata);
				stream.metadata.set(dictionary);
				checkcode(libavcodec.avcodec_parameters_from_context(stream.codecpar.get(), codecCtx));

				stream.time_base.num.set(codecCtx.time_base.num.get());
				stream.time_base.den.set(codecCtx.time_base.den.get());
				stream.index.set(formatCtx.nb_streams.get() - 1);
				stream.id.set(formatCtx.nb_streams.get() - 1);

				this.codecTimeBaseNum = codecCtx.time_base.num.get();
				this.codecTimeBaseDen = codecCtx.time_base.den.get();
			}
        }

		abstract void initCodecCtx(B builder);

		protected void submitFrame(AVFrame frame, int duration) {
			if (filters == null) {
				encodeFrame(frame);
			} else {
				Feeder.feed(frame,
					inputFrame -> filters.submitFrame(inputFrame),
					outputFrame -> {
						encodeFrame(outputFrame);
						libavutil.av_frame_unref(outputFrame);
					});
			}
		}

        private void encodeFrame(AVFrame frame) {

			String stream = "Encoder: stream " + streamIndex + ": ";

			if (frame == null) {
				logger.debug(stream + "flush", new VelvetVideoLogAreaBundle.Encoder());
			} else {
				logger.debug(stream + "send frame for encoding, PTS=" + frame.pts.get(), new VelvetVideoLogAreaBundle.Encoder());
			}
        	checkcode(libavcodec.avcodec_send_frame(codecCtx, frame));
            for (;;) {
            	libavcodec.av_init_packet(packet);
                int res = libavcodec.avcodec_receive_packet(codecCtx, packet);
                if (res == LibAVUtil.AVERROR_EAGAIN || res == LibAVUtil.AVERROR_EOF)
                    break;
                checkcode(res);
                packet.stream_index.set(streamIndex);

				logger.debug("returned packet PTS/DTS: " +
						packet.pts.get() + "/" + packet.dts.get() +
						", duration=" + packet.duration.get() +
						", " + packet.size.get() + " bytes",
						new VelvetVideoLogAreaBundle.Encoder()
				);

                fixEncodedPacketPtsDtsDuration();
        		if (packet.pts.get() != nextExpectedPts) {
					logger.warn("Encoder: expected PTS mismatch: expected " + nextExpectedPts + ", actual " + packet.pts.get(), new VelvetVideoLogAreaBundle.Encoder());
        		}
        		nextExpectedPts = packet.pts.get() + packet.duration.get();
                output.accept(packet);

                libavcodec.av_packet_unref(packet);
            }
        }

		abstract protected void fixEncodedPacketPtsDtsDuration();

		@Override
		public void close() {
			if (codecOpened) {
				submitFrame(null, defaultFrameDuration);
				libavcodec.avcodec_close(codecCtx);
				libavcodec.avcodec_free_context(new Pointer[] { Struct.getMemory(codecCtx) });
			}
			super.close();
		}
    }

    private class VideoEncoderStreamImpl extends AbstractEncoderStreamImpl<VideoEncoderBuilderImpl> implements IVideoEncoderStream {

		private VideoFrameHolder frameHolder;
		private final Map<Long, Integer> frameDurationCache = new HashMap<>();

		public VideoEncoderStreamImpl(VideoEncoderBuilderImpl builder, AVFormatContext formatCtx,
				Consumer<AVPacket> output) {
			super(builder, formatCtx, output);
		}

		@Override
		void initCodecCtx(@NotNull VideoEncoderBuilderImpl builder) {
            codecCtx.width.set(builder.width == null ? 1 : builder.width);
            codecCtx.height.set(builder.height == null ? 1 : builder.height);
            int firstFormat = codec.pix_fmts.get().getInt(0);
            codecCtx.pix_fmt.set(firstFormat);
		}

		@Override
        public void encode(BufferedImage image) {
			encode(image, 1);
		}

		@Override
		protected void submitFrame(AVFrame frame, int duration) {
			if (frame != null) {
				frameDurationCache.put(frame.pts.get(), duration);
			}
			super.submitFrame(frame, duration);
		}

		@Override
        public void encode(@NotNull BufferedImage image, int duration) {
            int width = image.getWidth();
            int height = image.getHeight();

            if (!this.codecOpened) {
            	codecCtx.width.set(width);
                codecCtx.height.set(height);

                checkcode(libavcodec.avcodec_open2(codecCtx, codecCtx.codec.get(), new Pointer[] {codecOpts}));
                checkcode(libavcodec.avcodec_parameters_from_context(stream.codecpar.get(), codecCtx));

                codecOpened = true;
                if (filterString != null)
                	this.filters = new Filters(codecCtx, filterString);
            } else {
            	if (codecCtx.width.get() != width || codecCtx.height.get() != height) {
            		throw new VelvetVideoException("Image dimensions do not match, expected " + codecCtx.width.get() + "x" + codecCtx.height.get());
            	}
            }

            if (frameHolder == null) {
            	frameHolder = new VideoFrameHolder(width, height, AVPixelFormat.avformatOf(image.getType()), codecCtx.pix_fmt.get(), stream.time_base, true);
            }

            AVFrame frame = frameHolder.setPixels(image);
            frame.extended_data.set(frame.data[0].getMemory());
            frame.pts.set(nextPts);
            nextPts += (long) duration * defaultFrameDuration;
            submitFrame(frame, duration);
        }

		@Override
		protected void fixEncodedPacketPtsDtsDuration() {
			Integer dur = frameDurationCache.remove(packet.pts.get());
			if ((packet.duration.get() == 0 || packet.duration.get() == AVNOPTS_VALUE)) {
				if (dur == null)
					dur = 1;
				packet.duration.set((long) dur * defaultFrameDuration);
				logger.debug("Encoder: duration adjusted to " + packet.duration.get(), new VelvetVideoLogAreaBundle.Encoder());
			}
		}

		@Override
		public void close() {
			super.close();
			if (frameHolder != null) {
            	frameHolder.close();
            }
			if (filters != null) {
				filters.close();
			}
		}

    }

    private class AudioEncoderStreamImpl extends AbstractEncoderStreamImpl<AudioEncoderBuilderImpl> implements IAudioEncoderStream {

 		private AudioFrameHolder frameHolder;
		private AudioFormat inputSampleFormat;

		public AudioEncoderStreamImpl(AudioEncoderBuilderImpl builder, AVFormatContext formatCtx,
 				Consumer<AVPacket> output) {
 			super(builder, formatCtx, output);

 		}

		@Override
		void initCodecCtx(@NotNull AudioEncoderBuilderImpl builder) {

			this.inputSampleFormat = builder.inputFormat;
			Set<AVSampleFormat> supportedFormats = codec.sampleFormats();
			AVSampleFormat codecAVSampleFormat = BestMatchingAudioFormatConvertor.findBest(supportedFormats, inputSampleFormat);

			AudioFormat codecAudioFormat = codecAVSampleFormat.toAudioFormat((int)inputSampleFormat.getSampleRate(), inputSampleFormat.getChannels());

			codecCtx.sample_fmt.set(codecAVSampleFormat);
			codecCtx.sample_rate.set((int)codecAudioFormat.getSampleRate());
			codecCtx.channels.set(codecAudioFormat.getChannels());
			codecCtx.channel_layout.set(libavutil.av_get_default_channel_layout(codecAudioFormat.getChannels()));
			codecCtx.bit_rate.set(builder.bitrate == null ? 128 * 1024 : builder.bitrate); //TODO default audio bit rate ?

			checkcode(libavcodec.avcodec_open2(codecCtx, codecCtx.codec.get(), new Pointer[]{codecOpts}));
			frameHolder = new AudioFrameHolder(codecCtx.time_base, true, codecCtx, builder.inputFormat);
			this.codecOpened = true;
		}

		@Override
		protected void fixEncodedPacketPtsDtsDuration() {
			//long dur = packet.duration.get();
			//if (dur == codecCtx.frame_size.get() && (codecCtx.time_base.den.get() != stream.time_base.den.get() || codecCtx.time_base.num.get() != stream.time_base.num.get())) {
			//	logEncoder.atWarn().log("duration not converted");
				packet.pts.set(codecToStream(packet.pts.get()));
				packet.dts.set(codecToStream(packet.dts.get()));
				packet.duration.set(codecToStream(packet.duration.get()));
			//}
		}

		private long codecToStream(long dur) {
			return dur * stream.time_base.den.get() * codecCtx.time_base.num.get() / ((long) stream.time_base.num.get() * codecCtx.time_base.den.get());
		}

		@Override
		public void encode(byte[] samples) {
			encode(samples, 0);
		}

		@Override
		public void encode(byte[] samples, int offset) {
			int duration = frameHolder.put(samples, offset);
			AVFrame frame = frameHolder.frame();
			frame.pts.set(nextPts);
            nextPts += duration;
			submitFrame(frame, duration);
		}

		@Override
		public int frameBytes() {
			return frameHolder.frameBytes();
		}

		@Override
		public void close() {
			if (frameHolder != null) {
				frameHolder.close();
			}
			super.close();
		}
    }
    @Override
    public IMuxerBuilder muxer(String format) {
        return new MuxerBuilderImpl(format);
    }

    private class MuxerBuilderImpl implements IMuxerBuilder {

    	@RequiredArgsConstructor
    	private static class BuilderRec {

    		public BuilderRec(IVideoEncoderBuilder video) {
				this((VideoEncoderBuilderImpl) video, null, null);
			}

    		public BuilderRec(IAudioEncoderBuilder audio) {
				this(null, (AudioEncoderBuilderImpl) audio, null);
			}

			public BuilderRec(IRemuxerBuilder remuxer) {
				this(null, null, (RemuxerBuilderImpl) remuxer);
			}

			private final VideoEncoderBuilderImpl video;
			private final AudioEncoderBuilderImpl audio;
			private final RemuxerBuilderImpl remuxer;
    	}

        private final String format;
        private final List<BuilderRec> builders = new ArrayList<>();

        private final Map<String, String> metadata = new LinkedHashMap<>();

        public MuxerBuilderImpl(String format) {
            this.format = format;
        }

        @Override
        public IMuxerBuilder videoEncoder(IVideoEncoderBuilder encoderBuilder) {
        	builders.add(new BuilderRec(encoderBuilder));
            return this;
        }

        @Override
        public IMuxerBuilder audioEncoder(IAudioEncoderBuilder encoderBuilder) {
        	builders.add(new BuilderRec(encoderBuilder));
            return this;
        }

        @Override
        public IMuxerBuilder remuxer(IDecoderStream<?, ?, ?> decoder) {
			return remuxer(new RemuxerBuilderImpl(decoder));
		}

		@Override
		public IMuxerBuilder remuxer(IRemuxerBuilder remuxerBuilder) {
			builders.add(new BuilderRec(remuxerBuilder));
			return this;
		}

        @Override
        public IMuxerBuilder metadata(String key, String value) {
            metadata.put(key, value);
            return this;
        }

        @Contract("_ -> new")
		@Override
        public @NotNull IMuxer build(ISeekableOutput output) {
            return new MuxerImpl(output, this);
        }

        @Contract("_ -> new")
		@Override
        public @NotNull IMuxer build(File outputFile) {
            try {
                FileSeekableOutput output = new FileSeekableOutput(new FileOutputStream(outputFile));
                return new MuxerImpl(output, this);
            } catch (FileNotFoundException e) {
                throw new VelvetVideoException(e);
            }
        }
    }

    private class MuxerImpl implements IMuxer {
        private final LibAVFormat libavformat;
        private final List<VideoEncoderStreamImpl> videoStreams = new ArrayList<>();
        private final List<AudioEncoderStreamImpl> audioStreams = new ArrayList<>();
        private final List<RemuxerStreamImpl> remuxerStreams = new ArrayList<>();

        private final ISeekableOutput output;
        private final AVFormatContext formatCtx;
        private final IOCallback callback;

        private MuxerImpl(ISeekableOutput output, @NotNull MuxerBuilderImpl builder) {

            this.libavformat = JNRHelper.load(LibAVFormat.class, Libraries.avformat, Libraries.avformat_version);
            this.output = output;
            this.formatCtx = createMuxerFormatContext(builder.format, builder.metadata);
            this.callback = new IOCallback();
            initCustomAvio(false, formatCtx, callback);

            Consumer<AVPacket> packetStream = packet -> {
				logger.debug("writing packet PTS/DTS = " + packet.pts.get() + "/" + packet.dts.get() + ", duration=" + packet.duration.get() + ", " + packet.size.get() + " bytes", new VelvetVideoLogAreaBundle.Muxer());
            	checkcode(libavformat.av_write_frame(formatCtx, packet));
            };

            builder.builders.forEach(brec ->  {
            		if (brec.video != null) {
            			videoStreams.add(new VideoEncoderStreamImpl(brec.video, formatCtx, packetStream));
            		} else if (brec.audio != null) {
            			audioStreams.add(new AudioEncoderStreamImpl(brec.audio, formatCtx, packetStream));
            		} else if (brec.remuxer != null) {
            			remuxerStreams.add(new RemuxerStreamImpl(brec.remuxer, formatCtx, packetStream));
            		} else {
            			throw new VelvetVideoException("Unknown video stream builder type");
            		}
            	 });

            checkcode(libavformat.avformat_write_header(formatCtx, null));

            // TODO: fix dis hack
            videoStreams.forEach(AbstractMuxerStreamImpl::init);
            audioStreams.forEach(AbstractMuxerStreamImpl::init);
            remuxerStreams.forEach(RemuxerStreamImpl::init);
        }

		@Override
		public IVideoEncoderStream videoEncoder(int index) {
			return videoStreams.stream().filter(vs -> vs.streamIndex == index).findFirst()
					.orElseThrow(() -> new VelvetVideoException("No video stream found with index " + index));
		}

		@Override
		public IAudioEncoderStream audioEncoder(int index) {
			return audioStreams.stream().filter(vs -> vs.streamIndex == index).findFirst()
					.orElseThrow(() -> new VelvetVideoException("No audio stream found with index " + index));
		}

		@Override
		public IRemuxerStream remuxer(int index) {
			return remuxerStreams.stream().filter(vs -> vs.streamIndex == index).findFirst()
					.orElseThrow(() -> new VelvetVideoException("No remuxer stream found with index " + index));
		}

		private class IOCallback implements ICustomAvioCallback {

			@Contract("_, _, _ -> param3")
			@Override
			public int read_packet(Pointer opaque, @NotNull Pointer buf, int buf_size) {
				// TODO [low] perf: prealloc buffer
				byte[] bytes = new byte[buf_size];
				buf.get(0, bytes, 0, buf_size);
				output.write(bytes);
				return buf_size;
			}

            @Override
            public int seek(Pointer opaque, int offset, int whence) {
                // TODO [low] support other whence values
                if (whence != 0)
                    throw new IllegalArgumentException();
                output.seek(offset);
                return offset;
            }
        }

        @Override
        public void close() {
            // flush encoders
        	// TODO: order ??
            for (VideoEncoderStreamImpl encoder : videoStreams) {
                encoder.close();
            }
            for (AudioEncoderStreamImpl encoder : audioStreams) {
                encoder.close();
            }
            for (RemuxerStreamImpl encoder : remuxerStreams) {
                encoder.close();
            }
            // flush muxer
            do {
				logger.debug("flushing", new VelvetVideoLogAreaBundle.Muxer());
            } while (checkcode(libavformat.av_write_frame(formatCtx, null)) == 0);

			logger.debug("writing trailer", new VelvetVideoLogAreaBundle.Muxer());
            checkcode(libavformat.av_write_trailer(formatCtx));
            // dispose resources
            AVIOContext avio = formatCtx.pb.get();
            libavutil.av_free(avio.buffer.get());
            libavformat.avio_context_free(new Pointer[] {Struct.getMemory(avio)});
            libavutil.av_dict_free(new Pointer[] {formatCtx.metadata.get()});
            formatCtx.metadata.set((Pointer)null);
            libavformat.avformat_free_context(formatCtx);
            output.close();
        }

    }

    @Override
    public IDemuxer demuxer(ISeekableInput input) {
        return new DemuxerImpl(input);
    }

    public class DemuxerImpl implements IDemuxer {

        private final AVFormatContext formatCtx;
        private final ISeekableInput input;
        private final IOCallback callback;

        private final AVPacket packet;
        private final Map<Integer, DecoderVideoStreamImpl> indexToVideoStream = new LinkedHashMap<>();
        private final Map<Integer, DecoderAudioStreamImpl> indexToAudioStream = new LinkedHashMap<>();
        private final List<AbstractDecoderStream> allStreams = new ArrayList<>();
		private int flushStreamIndex = 0;

        public DemuxerImpl(ISeekableInput input) {
            this.input = input;
            this.packet = libavcodec.av_packet_alloc();
            this.formatCtx = libavformat.avformat_alloc_context();
            this.callback = new IOCallback();
            initCustomAvio(true, formatCtx, callback);

            PointerByReference ptrctx = new PointerByReference(Struct.getMemory(formatCtx));
            int res = libavformat.avformat_open_input(ptrctx, null, null, null);
            if (res == LibAVUtil.AVERROR_INVALIDDATA) {
                throw new VelvetVideoException("Unknown container format");
            }
            checkcode(res);
            checkcode(libavformat.avformat_find_stream_info(formatCtx, null));

            long nb = formatCtx.nb_streams.get();
            Pointer pointer = formatCtx.streams.get();
            for (int i=0; i<nb; i++) {
                Pointer mem = pointer.getPointer((long) i * pointer.getRuntime().addressSize());
                AVStream avstream = JNRHelper.struct(AVStream.class, mem);
                int mediaType = avstream.codec.get().codec_type.get();
				if (mediaType == LibAVCodec.AVMEDIA_TYPE_VIDEO) {
                    avstream.codec.get().strict_std_compliance.set(-2);
                    DecoderVideoStreamImpl decoder = new DecoderVideoStreamImpl(avstream, defaultName(avstream, i));
                    indexToVideoStream.put(i, decoder);
                    allStreams.add(decoder);
                } else if (mediaType == LibAVCodec.AVMEDIA_TYPE_AUDIO) { // TODO dry
                    avstream.codec.get().strict_std_compliance.set(-2);
                    DecoderAudioStreamImpl decoder = new DecoderAudioStreamImpl(avstream, defaultName(avstream, i));
                    indexToAudioStream.put(i, decoder);
                    allStreams.add(decoder);
                }
            }
        }

        private class IOCallback implements ICustomAvioCallback {

            @Override
            public int read_packet(Pointer opaque, Pointer buf, int buf_size) {
                byte[] bytes = new byte[buf_size];
                int bts;
                bts = input.read(bytes);
                if (bts > 0) {
                    buf.put(0, bytes, 0, bts);
                }
                return bts;
            }

            @Override
            public int seek(Pointer opaque, int offset, int whence) {

                final int SEEK_SET = 0;   /* set file offset to offset */
                // final int SEEK_CUR = 1;   /* set file offset to current plus offset */
                final int SEEK_END = 2;   /* set file offset to EOF plus offset */
                final int AVSEEK_SIZE = 0x10000;   /* set file offset to EOF plus offset */

                if (whence == SEEK_SET)
                    input.seek(offset);
                else if (whence == SEEK_END)
                    input.seek(input.size() - offset);
                else if (whence == AVSEEK_SIZE)
                    return (int) input.size();
                else throw new VelvetVideoException("Unsupported seek operation " + whence);
                return offset;
            }

        }

        @Override
		public IDecodedPacket<?> nextPacket() {
        	return Feeder.next(this::nextAVPacket, this::decodePacket);
        }

        @Override
        public IRawPacket nextRawPacket() {
        	AVPacket packet = nextAVPacket();
        	if (packet == null) {
        		return null;
        	}
        	return new RawPacket(packet);
        }

        private @Nullable AVPacket nextAVPacket() {
			libavcodec.av_init_packet(packet);
			packet.data.set((Pointer) null); // TODO Wouldn't it overwrite ?
			packet.size.set(0);
			int res = libavformat.av_read_frame(formatCtx, packet);
			if (res == LibAVUtil.AVERROR_EOF || res == -1) {
				logger.debug("muxer empty", new VelvetVideoLogAreaBundle.Demuxer());
				return null;
			}
			checkcode(res);
			logger.debug("stream " + packet.stream_index.get() +
					"read packet PTS/DTs=" + packet.pts.get() + "/" + packet.dts.get() + " " +
					"duration=" + packet.duration.get() + " " +
					"size=" + packet.size.get() + " bytes",
					new VelvetVideoLogAreaBundle.Demuxer()
			);
			return packet;
        }

        @Override
        public Stream<IDecodedPacket<?>> packetStream() {
        	// return Stream.generate(this::nextPacket).takeWhile(el -> el != null);
        	return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false);
        }

        @Override
        public Iterator<IDecodedPacket<?>> iterator() {
        	return iteratorFromSupplier(this::nextPacket);
        }


        /**
		 * @return null means "PACKET HAS NO OUTPUT DATA, GET NEXT PACKET"
		 */
		private IDecodedPacket<?> decodePacket(AVPacket p) {
			if (p != null) {
				return decodeRawPacket(p);
			} else {
				return flushNextStream();
			}
		}

		private IDecodedPacket<?> decodeRawPacket(@NotNull AVPacket p) {
			int index = p.stream_index.get();
			DecoderVideoStreamImpl videoStream = indexToVideoStream.get(index);
			if (videoStream != null)
				return videoStream.decodePacket(p);
			DecoderAudioStreamImpl audioStream = indexToAudioStream.get(index);
			if (audioStream != null)
				return audioStream.decodePacket(p);
			logger.warn("received packet of unknown stream " + index, new VelvetVideoLogAreaBundle.Demuxer());
			return new UnknownPacket();
		}

		private @Nullable IDecodedPacket<?> flushNextStream() {
			for (; flushStreamIndex < allStreams.size(); flushStreamIndex++) {
				logger.debug("flushing demuxer stream=" + flushStreamIndex, new VelvetVideoLogAreaBundle.Demuxer());
				AbstractDecoderStream stream = allStreams.get(flushStreamIndex);
				IDecodedPacket<?> packet = stream.decodePacket(null);
				if (packet != null) {
					return packet;
				}
			}
			return null;
		}

		private class DecoderVideoStreamImpl extends AbstractDecoderStream implements IVideoDecoderStream {

			public DecoderVideoStreamImpl(AVStream avstream, String name) {
				super(avstream, name);
			}

			@Contract(" -> new")
			@Override
			public @NotNull IVideoStreamProperties properties() {
				int timebase_n = avstream.time_base.num.get();
				int timebase_d = avstream.time_base.den.get();
				long duration = avstream.duration.get() * 1000000000L * timebase_n / timebase_d;
				if (duration == 0)
					duration = formatCtx.duration.get() * 1000L;
				long frames = avstream.nb_frames.get();
				int width = codecCtx.width.get();
				int height = codecCtx.height.get();
				AVCodec codec = libavcodec.avcodec_find_decoder(codecCtx.codec_id.get());
				double framerate = (double) avstream.avg_frame_rate.num.get() / avstream.avg_frame_rate.den.get();
				return new VideoStreamProperties(codec.name.get(), framerate, duration, frames, width, height);
			}

			@Override
			public @Nullable IVideoFrame nextFrame() {
				IDecodedPacket<?> packet;
				while((packet = nextPacket()) != null) {
					if (packet.is(MediaType.Video) && packet.stream() == this) {
						return packet.asVideo();
					}
				}
				return null;
			}

			@Contract(value = " -> new", pure = true)
			@Override
			public @NotNull Iterator<IVideoFrame> iterator() {
				return iteratorFromSupplier(this::nextFrame);
			}

			@Override
			public IVideoDecoderStream seek(long frameNumber) {
				seekToFrame(frameNumber);
				return this;
			}

			@Override
			public IVideoDecoderStream seekNano(long ns) {
				seekToNano(ns);
				return this;
			}

            @Contract(" -> new")
			@Override
            protected @NotNull IFrameHolder createFrameHolder() {
            	return new VideoFrameHolder(codecCtx.width.get(), codecCtx.height.get(), codecCtx.pix_fmt.get(), AVPixelFormat.AV_PIX_FMT_BGR24, avstream.time_base, false);
            }

		}

		private class DecoderAudioStreamImpl extends AbstractDecoderStream implements IAudioDecoderStream {

			private final AudioFormat targetFormat;

			public DecoderAudioStreamImpl(AVStream avstream, String name) {
				super(avstream, name);
		    	AudioFormat suggestedFormat = codecCtx.sample_fmt.get().destFormat().toAudioFormat(codecCtx.sample_rate.get(), codecCtx.channels.get());
		    	targetFormat = new BestMatchingAudioFormatConvertor().apply(suggestedFormat);
				logger.info("stream " + index() + ": audio format [" + targetFormat + "]", new VelvetVideoLogAreaBundle.Decoder());
		    	if (!targetFormat.equals(suggestedFormat)) {
					logger.warn("Audio format converted [" + suggestedFormat + "] -> [" + targetFormat + "]", new VelvetVideoLogAreaBundle.Decoder());
		    	}
			}

			@Override
			public @Nullable IAudioFrame nextFrame() {
				IDecodedPacket<?> packet;
				while((packet = nextPacket()) != null) {
					if (packet.is(MediaType.Audio) && packet.stream() == this) {
						return packet.asAudio();
					}
				}
				return null;
			}

			@Contract(value = " -> new", pure = true)
			@Override
			public @NotNull Iterator<IAudioFrame> iterator() {
				return iteratorFromSupplier(this::nextFrame);
			}

			@Override
			public @NotNull IAudioStreamProperties properties() {
				// TODO DRY
				int timebase_n = avstream.time_base.num.get();
				int timebase_d = avstream.time_base.den.get();
				long duration = avstream.duration.get() * 1000000000L * timebase_n / timebase_d;
				long frames = avstream.nb_frames.get(); // TODO: unreliable
				AVCodec codec = libavcodec.avcodec_find_decoder(codecCtx.codec_id.get());
				return new AudioStreamPropertiesImpl(codec.name.get(), targetFormat, duration, frames);
			}

			@Override
			public IAudioDecoderStream seek(long frameNumber) {
				// TODO
				throw new VelvetVideoException("Not yet implemented");
			}

			@Override
			public IAudioDecoderStream seekNano(long ns) {
				// TODO
				throw new VelvetVideoException("Not yet implemented");
			}

			@Contract(" -> new")
			@Override
			protected @NotNull IFrameHolder createFrameHolder() {
				return new AudioFrameHolder(avstream.time_base, false, codecCtx, targetFormat);
			}

		}

        public abstract class AbstractDecoderStream implements AutoCloseable {
            protected final AVStream avstream;
            protected final AVCodecContext codecCtx;

            private final String name;

            protected IFrameHolder frameHolder;
            private final int index;
            private long skipToPts = -1;
			private Filters filters;

            public AbstractDecoderStream(@NotNull AVStream avstream, String name) {
                this.avstream = avstream;
                this.name = name;
                this.index = avstream.index.get();
                this.codecCtx = avstream.codec.get();
                AVCodec codec = libavcodec.avcodec_find_decoder(codecCtx.codec_id.get());
                checkcode(libavcodec.avcodec_open2(codecCtx, codec, null));
				logger.info("stream " + avstream.index.get() +
								": timebase: " +avstream.time_base.num.get() + "/" + avstream.time_base.den.get() +
								", codec [" + codec.name.get() + "] timebase " +
								codecCtx.time_base.num.get() + "/" + codecCtx.time_base.den.get(),
						new VelvetVideoLogAreaBundle.Decoder()
				);
            }

            /**
             * @return null means "PACKET HAS NO OUTPUT DATA, GET NEXT PACKET"
             */
            IDecodedPacket<?> decodePacket(AVPacket pack) {
            	for (;;) {
	            	AVFrame frame = feedPacket(pack);
	            	if (filters != null) {
	           			frame = filters.submitFrame(frame);
	            	}
	            	if (frame == null)
	            		return null;
	            	long pts = frame.pts.get();
					logger.debug("delivered frame pts=" + pts, new VelvetVideoLogAreaBundle.Decoder());
	                if (skipToPts != -1) {
	                	if (pts == AVNOPTS_VALUE) {
	                		throw new VelvetVideoException("Cannot seek when decoded packets have no PTS. Looks like neither codec no container keep timing information.");
	                	}
	                    if (pts < skipToPts) {
							logger.debug("...but need to skip more to pts=" + skipToPts, new VelvetVideoLogAreaBundle.Decoder());
							if (pack == null)
								continue;
							return null;
						} else if (pts > skipToPts) {
							logger.warn(" ...unexpected position: PTS=" + pts + " missed target PTS=" + skipToPts, new VelvetVideoLogAreaBundle.Decoder());
							if (pack == null)
								continue;
							return null;
	                    }
	                    skipToPts = -1;
	                }
	                IDecodedPacket<?> decodedPacket = frameHolder.decode(frame, this);
	                if (filters !=null)
	                	libavutil.av_frame_unref(frame);
	                return decodedPacket;
            	}
            }

            AVFrame feedPacket(AVPacket pack) {
            	 int res1 = libavcodec.avcodec_send_packet(codecCtx, pack);
            	 if (res1 != LibAVUtil.AVERROR_EOF) {
            		 checkcode(res1);
            	 }
            	 if (frameHolder == null) {
            		 this.frameHolder = createFrameHolder();
            	 }
            	 int res = libavcodec.avcodec_receive_frame(codecCtx, frameHolder.frame());
            	 if (res == LibAVUtil.AVERROR_EOF || pack != null && res == LibAVUtil.AVERROR_EAGAIN)
            		 return null;
            	 checkcode(res);
				 logger.debug("decoded frame pts=" + frameHolder.pts() + " dur=" + libavutil.av_frame_get_pkt_duration(frameHolder.frame()), new VelvetVideoLogAreaBundle.Decoder());
            	 libavcodec.av_packet_unref(packet);
            	 return frameHolder.frame();
            }

            abstract protected IFrameHolder createFrameHolder();

			public String name() {
                return name;
            }

            public int index() {
            	return index;
            }

            public Map<String, String> metadata() {
                Pointer dictionary = avstream.metadata.get();
                return libavutil.dictionaryToMap(dictionary);
            }

            public void seekToFrame(long frameIndex) {
            	// TODO: this won't work for var-duration streams
                long cn = codecCtx.time_base.num.get();
                long cd = codecCtx.time_base.den.get();
                long defaultFrameDur = cn * avstream.time_base.den.get() * codecCtx.ticks_per_frame.get() / (cd * avstream.time_base.num.get());
                long pts = frameIndex * defaultFrameDur;
				logger.debug("seeking to frame " + frameIndex + ", target pts=" + pts, new VelvetVideoLogAreaBundle.Decoder());
                seekToPts(pts);
            }


            public void seekToNano(long nanostamp) {
                long pts = nanostamp * avstream.time_base.den.get() / avstream.time_base.num.get() / 1000000;
				logger.debug("seeking to t=" + nanostamp + " ns, target pts=" + pts, new VelvetVideoLogAreaBundle.Decoder());
                seekToPts(pts);
            }

			private void seekToPts(long pts) {
				checkcode(libavformat.av_seek_frame(formatCtx, this.index, pts, LibAVFormat.AVSEEK_FLAG_FRAME | LibAVFormat.AVSEEK_FLAG_BACKWARD));
                libavcodec.avcodec_flush_buffers(codecCtx);
                this.skipToPts  = pts;
                flushStreamIndex = 0;
                if (filters != null)
                	filters.reset();
			}

			public IRawPacket nextRawPacket() {
				AVPacket p;
				while ((p = DemuxerImpl.this.nextAVPacket()) != null) {
					IRawPacket rp = (p.stream_index.get() == index) ? new RawPacket(p) : null;
					libavcodec.av_packet_unref(p);
					if (rp != null) {
						return rp;
					}
				}
				return null;
			}

			public void setFilter(String filterString) {
				if (filterString != null)
					this.filters = new Filters(codecCtx, filterString);
			}

			@Override
			public void close() {
				if (filters != null) {
					filters.close();
				}
				if (frameHolder != null) {
					frameHolder.close();
				}
				libavcodec.avcodec_close(codecCtx);
//				libavcodec.avcodec_free_context(new Pointer[] { Struct.getMemory(codecCtx) });
			}

        }

        @Override
        public List<? extends IVideoDecoderStream> videoStreams() {
            return new ArrayList<>(indexToVideoStream.values());
        }

        @Override
        public IVideoDecoderStream videoStream(int index) {
        	return indexToVideoStream.get(index);
        }

        @Override
        public List<? extends IAudioDecoderStream> audioStreams() {
            return new ArrayList<>(indexToAudioStream.values());
        }

        @Override
        public IAudioDecoderStream audioStream(int index) {
        	return indexToAudioStream.get(index);
        }

        @Override
        public List<IDecoderStream<?, ?, ?>> streams() {
        	List<IDecoderStream<?, ?, ?>> streams = new ArrayList<>();
        	streams.addAll(indexToVideoStream.values());
        	streams.addAll(indexToAudioStream.values());
        	return streams;
        }

        @Override
        public Map<String, String> metadata() {
            Pointer dictionary = formatCtx.metadata.get();
            return libavutil.dictionaryToMap(dictionary);
        }

        @Override
        public IContainerProperties properties() {
        	// TODO: how to get single format ?
        	return new MuxerProperties(formatCtx.iformat.get().name.get(), formatCtx.duration.get() * 1000L);
        }

        @Override
        public void close() {
        	libavcodec.av_packet_free(new Pointer[] {Struct.getMemory(packet)});
            this.allStreams.forEach(AbstractDecoderStream::close);
            // dispose resources
            AVIOContext avio = formatCtx.pb.get();
            libavutil.av_free(avio.buffer.get());
            libavformat.avio_context_free(new Pointer[] {Struct.getMemory(avio)});
            libavutil.av_dict_free(new Pointer[] {formatCtx.metadata.get()});
            formatCtx.metadata.set((Pointer)null);
            libavformat.avformat_free_context(formatCtx);
            input.close();
        }

        @Override
        public String toString() {
        	return "Demuxer " + properties();
        }

    }

	private static <T> Iterator<T> iteratorFromSupplier(Supplier<T> supplier) {
		return new Iterator<T>() {

    		private T next = supplier.get();

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public T next() {
				if (next == null)
					throw new NoSuchElementException();
				T ret = next;
				next = supplier.get();
				return ret;
			}
		};
	}
}

@Accessors(fluent = true)
@Value
@ToString
class MuxerProperties implements IContainerProperties {
    String format;
    long nanoduration;
}

@Accessors(fluent = true)
@Value
@ToString
class VideoStreamProperties implements IVideoStreamProperties {
    String codec;
    double framerate;
    long nanoduration;
    long frames;
    int width;
    int height;
}

@Accessors(fluent = true)
@Getter
@ToString
class RawPacket implements IRawPacket {

	public RawPacket(AVPacket packet) {
		this.streamIndex = packet.stream_index.get();
		this.bytes = packet.bytes();
		this.pts = packet.pts.get();
		this.dts = packet.dts.get();
		this.duration = packet.duration.get();
	}
    private final int streamIndex;
    private final long pts;
    private final long dts;
    private final long duration;
    private final byte[] bytes;
}

class UnknownPacket implements IDecodedPacket<IDecoderStream<?, ?, ?>> {

	@Override
	public IDecoderStream<?, ?, ?> stream() {
		return null;
	}

	@Override
	public MediaType type() {
		return null;
	}

	@Override
	public long nanostamp() {
		return 0;
	}

	@Override
	public long nanoduration() {
		return 0;
	}
}
