package com.toxicstoxm.velvet_video_remastered.impl.jnr;

import jnr.ffi.Runtime;
import jnr.ffi.Struct;

public class AVOption extends Struct {
    public AVOption(Runtime runtime) {
        super(runtime);
    }
    public String name = new AsciiStringRef();
}
