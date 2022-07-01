package com.displaynote.achoplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import com.displaynote.achoplayer.video.DataPacket;
import com.displaynote.achoplayer.video.VideoDecoder;
import com.google.gson.Gson;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private AutoFitTextureView mAutofitTextureView;
    private VideoDecoder mVideoDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAutofitTextureView = findViewById(R.id.textureView);
        mAutofitTextureView.setSurfaceTextureListener(this);
    }

    private void openAndReadFile(){

        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.macbookpro_m1_streaming);

            FileInputStream fis = afd.createInputStream();
            FileChannel channel = fis.getChannel();
            int fileSize = (int) channel.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize);
            channel.read(byteBuffer);
            byteBuffer.flip();

            for (ByteBuffer bb : H264Utils.splitFrame(byteBuffer)) {

                DataPacket packet = new DataPacket();
                packet.ts = System.nanoTime();

                byte[] nalu = new byte[bb.limit()];
                bb.get(nalu);

                // Needed to add the mark of nalu again as h264utils removes it, not sure on how
                // avoid this. But who cares.. this is just a demo app to check decode.
                byte[] payloadOut = new byte[nalu.length + 4];
                byte[] nal_limit = new byte[]{0x00, 0x00, 0x00, 0x01};
                System.arraycopy(nal_limit, 0, payloadOut, 0, nal_limit.length);
                System.arraycopy(nalu, 0, payloadOut, nal_limit.length, nalu.length);

                packet.data_len = payloadOut.length;
                packet.data = ByteBuffer.wrap(payloadOut);
                mVideoDecoder.addData(packet);
            }

            mVideoDecoder.stop();

        } catch (IOException e) {
            //log the exception
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        if(mVideoDecoder==null){
            mVideoDecoder = new VideoDecoder(surfaceTexture);
            mVideoDecoder.setSize(width, height);
            //mVideoDecoder.start(); //nooope

            new Thread(() -> openAndReadFile()).start();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

    }
}