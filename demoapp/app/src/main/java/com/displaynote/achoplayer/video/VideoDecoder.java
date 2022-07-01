package com.displaynote.achoplayer.video;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VideoDecoder {

    private static final String TAG = VideoDecoder.class.getName();
    private static final int QUEUE_CAPACITY = 16;

    private MediaCodec decoder;
    private MediaFormat format;
    private Surface surface;
    private byte[] configParam;
    private final String mimeType;

    private int width;
    private int height;

    private final BlockingQueue<DataPacket> queue;
    private volatile boolean finished = false;
    private boolean first_packet = true;

    private volatile long last_ts = -1;

    public VideoDecoder(SurfaceTexture surfaceTexture) {
        this.surface = new Surface(surfaceTexture);
        this.queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.mimeType = MediaFormat.MIMETYPE_VIDEO_AVC;
        clearSurface();
    }

    public void setConfigParam(byte[] configParam){
        this.configParam = configParam;
    }

    public void setSize(int width, int height){
        this.width = width;
        this.height = height;
    }

    private void clearSurface() {
        Log.d(TAG, "clearSurface() " + this.surface);
        VideoUtils.clearSurface(this.surface);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "setSurfaceTexture() " + surfaceTexture);
        if (decoder != null) {
            if (this.surface != null) {
                this.surface.release();
            }
            this.surface = new Surface(surfaceTexture);
            decoder.setOutputSurface(surface);
        }
    }

    public synchronized void addData(DataPacket packet) {
        if(finished) {
            Log.w(TAG,"addData() This decoder is finished, you should create a new one.");
            return;
        }

        if(first_packet){
            setConfigParam(packet.data.array());
            start();
            first_packet = false;
        }

        try {
            queue.put(packet);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public synchronized void stop() {
        if(finished){
            Log.w(TAG,"stop() This decoder is finished.. doing nothing");
            return;
        }
        finished = true;
        FinalMarker.addFinalMarker(queue, QUEUE_CAPACITY);
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
        Log.d(TAG, "Decoder stopped!");
    }

    private void prepare() throws IOException {
        Log.d(TAG, "prepare() Create \"" + mimeType + "\" Media Format with width:" + width + " height:" + height);
        format = MediaFormat.createVideoFormat(mimeType, width, height);
        if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AVC))
            VideoUtils.setFormatFromParam(format, configParam);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            Log.i(TAG,"Trying to enable low latency decoding mode");
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.i(TAG,"Enable priority and max operating rate");
            format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE);
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        }
        decoder = MediaCodec.createDecoderByType(mimeType);
        decoder.configure(format, surface, null, 0);

        // Have a look to https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java?msclkid=4d12f624b13a11ec92e36cee223df641
    }

    public synchronized void start() {
        if(decoder!=null){
            Log.w(TAG,"start() Decoder is already created.. doing nothing");
            return;
        }
        try {
            prepare();
            decoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int inputBufferId) {

                    if (finished)
                        return;

                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                    if (inputBuffer == null) return;

                    try {
                        inputBuffer.clear();

                        // This is to avoid getting the system MediaCodec thread blocked as
                        // waiting for our queue, then we're adding a marker to determine we finshed.
                        DataPacket datap = queue.take();
                        if (FinalMarker.isFinalMarker(datap)) {
                            // Finished.
                            queue.clear();
                            return;
                        }

                        datap.data.flip();
                        datap.data.limit(datap.data_len);
                        datap.data.rewind();

                        inputBuffer.put(datap.data);

                        Log.d(TAG, "Pushing buffer "+inputBufferId+" thread:"+Thread.currentThread().getId());

                        //decoder.queueSecureInputBuffer(inputBufferId, 0, null, (long) (datap.ts), 0);
                        decoder.queueInputBuffer(inputBufferId, 0, datap.data_len, (long) (datap.ts), 0);
                        Log.d(TAG, "Pushed buffer "+inputBufferId+" thread:"+Thread.currentThread().getId());
                        datap.data.clear();

                    } catch (Exception e) {
                        Log.e(TAG, "Exception feeding the decoder " + e.getMessage(), e);
                        e.printStackTrace();
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int outputBufferId, @NonNull MediaCodec.BufferInfo bufferInfo) {

                    if (finished)
                        return;

                    Log.d(TAG, "Releasing buffer "+outputBufferId+" thread:"+Thread.currentThread().getId());
                    try {
                        // As we're using a surface to paint there's no such OutputBuffer, but we need to call the release with the id
                        // to decide if we want to paint it or not.
                        decoder.releaseOutputBuffer(outputBufferId, System.nanoTime());
                        Log.d(TAG, "Released buffer "+outputBufferId+" thread:"+Thread.currentThread().getId());
                    } catch (IllegalStateException ignored) {
                    }
                }

                @Override
                public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException ex) {
                    Log.e(TAG, "Decoder onError()" + ex.getMessage(), ex);
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
                    Log.d(TAG, "Format changed to " + mediaFormat.toString() + " thread:" + Thread.currentThread().getId());

                    int w = 1 + mediaFormat.getInteger("crop-right") - mediaFormat.getInteger("crop-left");//oF.getInteger("width");
                    int h = 1 + mediaFormat.getInteger("crop-bottom") - mediaFormat.getInteger("crop-top");

                    Log.d(TAG, "Output format has changed: " + w + " " + h);
                }
            });
            decoder.start();
            Log.d(TAG, "start() Decoder started!");

        } catch (Exception ex) {
            Log.e(TAG, "Decoder error on main loop" + ex.getMessage(), ex);
            stop();
        }
    }
}