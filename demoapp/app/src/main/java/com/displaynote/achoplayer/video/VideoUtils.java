package com.displaynote.achoplayer.video;

import android.media.MediaFormat;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Base64;
import android.view.Surface;

import java.nio.ByteBuffer;

public class VideoUtils {

    public static void setFormatFromParam(MediaFormat format, byte[] configParam) {
        int offset = 0;
        int limit = configParam.length;
        byte[] dataArray = configParam;
        boolean[] prefixFlags = new boolean[3];
        int nalUnitType = 0;

        while (offset < limit) {
            int nextNalUnitOffset = findNalUnit(dataArray, offset, limit, prefixFlags);

            if (nextNalUnitOffset <= limit) {
                // We've seen the start of a NAL unit.
                // This is the length to the start of the unit. It may be negative if the NAL unit
                // actually started in previously consumed data.
                int lengthToNalUnit = nextNalUnitOffset - offset;
                if (nalUnitType == 7 || nalUnitType == 6) {
                    byte[] sps = new byte[lengthToNalUnit + 4];
                    System.arraycopy(dataArray, offset - 4, sps, 0, lengthToNalUnit + 4);
                    format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
                    String spsCoded = Base64.encodeToString(sps, 0);
                    int ojete = 1;
                } else if (nalUnitType == 8) {
                    byte[] pps = new byte[lengthToNalUnit + 4];
                    System.arraycopy(dataArray, offset - 4, pps, 0, lengthToNalUnit + 4);
                    format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
                    String ppsCoded = Base64.encodeToString(pps, 0);
                    int ojete = 1;
                }
                if (nextNalUnitOffset < limit)
                    nalUnitType = dataArray[nextNalUnitOffset + 3] & 0x1F;


                offset = nextNalUnitOffset + 3;
            }
        }
    }

    public static int findNalUnit(byte[] data, int startOffset, int endOffset,
                                  boolean[] prefixFlags) {
        int length = endOffset - startOffset;

        if (length == 0) {
            return endOffset;
        }

        if (prefixFlags != null) {
            if (prefixFlags[0]) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 3;
            } else if (length > 1 && prefixFlags[1] && data[startOffset] == 1) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 2;
            } else if (length > 2 && prefixFlags[2] && data[startOffset] == 0
                    && data[startOffset + 1] == 1) {
                clearPrefixFlags(prefixFlags);
                return startOffset - 1;
            }
        }

        int limit = endOffset - 1;
        // We're looking for the NAL unit start code prefix 0x000001. The value of i tracks the index of
        // the third byte.
        for (int i = startOffset + 2; i < limit; i += 3) {
            if ((data[i] & 0xFE) != 0) {
                // There isn't a NAL prefix here, or at the next two positions. Do nothing and let the
                // loop advance the index by three.
            } else if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1) {
                if (prefixFlags != null) {
                    clearPrefixFlags(prefixFlags);
                }
                return i - 2;
            } else {
                // There isn't a NAL prefix here, but there might be at the next position. We should
                // only skip forward by one. The loop will skip forward by three, so subtract two here.
                i -= 2;
            }
        }

        if (prefixFlags != null) {
            // True if the last three bytes in the data seen so far are {0,0,1}.
            prefixFlags[0] = length > 2
                    ? (data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                    : length == 2 ? (prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                    : (prefixFlags[1] && data[endOffset - 1] == 1);
            // True if the last two bytes in the data seen so far are {0,0}.
            prefixFlags[1] = length > 1 ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
                    : prefixFlags[2] && data[endOffset - 1] == 0;
            // True if the last byte in the data seen so far is {0}.
            prefixFlags[2] = data[endOffset - 1] == 0;
        }

        return endOffset;
    }

    private static void clearPrefixFlags(boolean[] prefixFlags) {
        prefixFlags[0] = false;
        prefixFlags[1] = false;
        prefixFlags[2] = false;
    }

    public static void clearSurface(Surface surface){
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(display, version, 0, version, 1);

        int[] attribList = {
             EGL14.EGL_RED_SIZE, 8,
             EGL14.EGL_GREEN_SIZE, 8,
             EGL14.EGL_BLUE_SIZE, 8,
             EGL14.EGL_ALPHA_SIZE, 8,
             EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
             EGL14.EGL_NONE, 0,
             EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(display, attribList, 0, configs, 0, configs.length, numConfigs, 0);

        EGLConfig config = configs[0];
        EGLContext context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, new int[]{
             EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
             EGL14.EGL_NONE
        }, 0);

        EGLSurface eglSurface = EGL14.eglCreateWindowSurface(display, config, surface,
             new int[]{
                 EGL14.EGL_NONE
             }, 0);

        EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context);
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        EGL14.eglSwapBuffers(display, eglSurface);
        EGL14.eglDestroySurface(display, eglSurface);
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(display, context);
        EGL14.eglTerminate(display);
    }
}
