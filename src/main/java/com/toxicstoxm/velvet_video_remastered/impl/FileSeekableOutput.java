package com.toxicstoxm.velvet_video_remastered.impl;

import com.toxicstoxm.velvet_video_remastered.ISeekableOutput;
import com.toxicstoxm.velvet_video_remastered.VelvetVideoException;
import org.jetbrains.annotations.NotNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public class FileSeekableOutput implements ISeekableOutput {

    private SeekableByteChannel channel;
    private FileOutputStream fos;

    public FileSeekableOutput(@NotNull FileOutputStream fos) {
        this.fos = fos;
        this.channel = fos.getChannel();
    }

    @Override
    public void write(byte[] bytes) {
        try {
            channel.write(ByteBuffer.wrap(bytes));
        } catch (IOException e) {
            throw new VelvetVideoException(e);
        }
    }

    @Override
    public void seek(long position) {
        try {
            channel.position(position);
        } catch (IOException e) {
            throw new VelvetVideoException(e);
        }
    }

    @Override
    public void close() {
        try {
            channel.close();
            fos.close();
        } catch (IOException e) {
            throw new VelvetVideoException(e);
        }
    }

}