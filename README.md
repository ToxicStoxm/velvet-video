# velvet-video-remastered

### A Maintained Fork of [zakgof/velvet-video](https://github.com/zakgof/velvet-video)

`velvet-video-remastered` is a Java library for encoding, decoding, muxing, and demuxing video and audio. It builds on the original `velvet-video` library, now maintained and extended to ensure compatibility with modern systems and to incorporate updates where possible.

The library provides a high-level API, abstracting the complexities of video and audio encoding technology. It’s designed to be user-friendly and versatile.

---

## Key Features

With `velvet-video-remastered`, you can easily:

- Create videos from still images
- Extract frames from videos
- Extract audio tracks from video files
- Remux video/audio between container formats (e.g., MP4 to MKV)
- Transcode (recompress) video/audio using different codecs (e.g., x264 to VP9, WAV to MP3)
- Adjust video timing (e.g., slow motion, timelapse)
- Merge videos or split them into segments
- Apply filters or transformations (before encoding or after decoding)

**Supported Formats and Codecs:**
- **Containers:** MP4, AVI, WebM, Matroska, and more
- **Codecs:** x264, HEVC, VP9, AV1, and others

**Performance:**  
`velvet-video-remastered` uses embedded FFmpeg libraries, ensuring native speed with hardware optimizations.

---

## Installation

### Gradle
To use the library, include the following dependency in your `build.gradle` file:
```groovy
implementation 'com.toxicstoxm.velvet-video-remastered:velvet-video-remastered:0.6.0'
```

The artifact is available on [Maven Central](https://central.sonatype.com/artifact/com.toxicstoxm.velvet-video-remastered/velvet-video-remastered).

---

## Setup for FFmpeg Support

`velvet-video-remastered` requires FFmpeg native components.

Supported platforms:
- Windows (64-bit)
- Linux (64-bit)

**Need support for another platform?** Feel free to reach out!

---

## Quick Start

### Encode Images into a Video
```java
IVelvetVideoLib lib = VelvetVideoLib().getInstance();
try (IMuxer muxer = lib.muxer("matroska")
    .video(lib.videoEncoder("libaom-av1").bitrate(800000))
    .build(new File("/some/path/output.mkv"))) {
         IEncoderVideoStream videoStream = muxer.videoStream(0);        
         videoStream.encode(image1);
         videoStream.encode(image2);
         videoStream.encode(image3);
}      
```

### Extract Images from a Video
```java
IVelvetVideoLib lib = VelvetVideoLib().getInstance();
try (IDemuxer demuxer = lib.demuxer(new File("/some/path/example.mp4"))) {
    IDecoderVideoStream videoStream = demuxer.videoStream(0);
    IFrame videoFrame;
    while ((videoFrame = videoStream.nextFrame()) != null) {
        BufferedImage image = videoFrame.image();
        // Use image as needed...
    }
}      
```

---

## Examples and Use Cases

Comprehensive examples are available in the original repository:  
[Examples Directory](https://github.com/zakgof/velvet-video/tree/master/src/example/java/com/zakgof/velvetvideo/example)

| Example                             | Description                                                      |
|-------------------------------------|------------------------------------------------------------------|
| **ImagesToVideoAndBack**            | Extract frame images from a video + compose a video from images. |
| **TranscodeVideoWithTimingEffects** | Transcode a video using another codec and apply slow motion.     |
| **RemuxVideo**                      | Repackage a video into another container without transcoding.    |
| **ScreenCaptureToVideo**            | Capture the desktop as a video.                                  |
| **AudioPlayback**                   | Play a compressed audio file.                                    |
| **ExtractAndTranscodeAudio**        | Extract audio tracks from a video and save them as MP3 files.    |

---

## Advanced Example: Video Player

For a fully functional video player example, visit:  
[velvet-video-player](https://github.com/zakgof/velvet-video-player)

---

## Licensing

`velvet-video-remastered` is dual-licensed under:

- **Apache License 2.0**
- **GPL 3.0 (or later)**

**SPDX License Identifier:**  
`Apache-2.0 OR GPL-3.0-or-later`