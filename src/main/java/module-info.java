module com.toxicstoxm.velvet_video_remastered {
    requires static lombok;
    requires org.apache.commons.io;
    requires org.jnrproject.ffi;
    requires org.slf4j;
    requires org.jetbrains.annotations;
    requires java.desktop;

    exports com.toxicstoxm.velvet_video_remastered.impl.jnr;
    exports com.toxicstoxm.velvet_video_remastered.impl.middle;
    exports com.toxicstoxm.velvet_video_remastered;
    exports com.toxicstoxm.velvet_video_remastered.impl;
    exports com.toxicstoxm.velvet_video_remastered.tools;
}