module com.toxicstoxm.velvet_video_remastered {
    requires org.apache.commons.io;
    requires org.jnrproject.ffi;
    requires YAJL;
    requires static lombok;
    requires org.jetbrains.annotations;
    requires java.desktop;

    exports com.toxicstoxm.velvet_video_remastered.impl.jnr;
    exports com.toxicstoxm.velvet_video_remastered.impl.middle;
    exports com.toxicstoxm.velvet_video_remastered.tools.logging;
    exports com.toxicstoxm.velvet_video_remastered;
    exports com.toxicstoxm.velvet_video_remastered.impl;
    exports com.toxicstoxm.velvet_video_remastered.tools;
}