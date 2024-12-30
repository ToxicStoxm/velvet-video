package com.toxicstoxm.velvet_video_remastered.tools.logging;

import com.toxicstoxm.YAJL.areas.LogAreaBundle;
import com.toxicstoxm.YAJL.areas.YAJLLogArea;

import java.awt.*;
import java.util.List;

public class VelvetVideoLogAreaBundle implements LogAreaBundle {

    public static class VelvetVideo extends YAJLLogArea {
        public VelvetVideo() {
            super("VELVET_VIDEO", new Color(113, 113, 113));
        }
    }

    public static class Muxer extends YAJLLogArea {
        public Muxer() {
            super("MUXER", new Color(113, 113, 113), List.of(new VelvetVideo().getName()));
        }
    }

    public static class Demuxer extends YAJLLogArea {
        public Demuxer() {
            super("DEMUXER", new Color(113, 113, 113), List.of(new VelvetVideo().getName()));
        }
    }

    public static class Encoder extends YAJLLogArea {
        public Encoder() {
            super("ENCODER", new Color(113, 113, 113), List.of(new VelvetVideo().getName()));
        }
    }

    public static class Decoder extends YAJLLogArea {
        public Decoder() {
            super("DECODER", new Color(113, 113, 113), List.of(new VelvetVideo().getName()));
        }
    }

    public static class Filter extends YAJLLogArea {
        public Filter() {
            super("FILTER", new Color(113, 113, 113), List.of(new VelvetVideo().getName()));
        }
    }
}
