package com.displaynote.achoplayer.video;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

/**
 * To avoid getting the thread of the MediaCodec blocked as waiting forever for a packet in the
 * queue while creating a new decoder, we put a special packet in queue instead to indicate
 * to the thread that we finished.
 */
public class FinalMarker {

    public static void addFinalMarker(BlockingQueue<DataPacket> queue, int capacity) {
        byte[] data = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        DataPacket datap = new DataPacket();
        datap.data = ByteBuffer.wrap(data);
        datap.ts = 0;
        datap.completion_id = 0;

        if (queue.size() >= capacity) //Queue is full make room for stop packet
        {
            DataPacket datapo = null;
            try {
                datapo = queue.take();
                if (datapo.completion_id != 0) {
                    // TODO: conn.sdkApi.VideoFrameComplete(conn.ID, datapo.completion_id);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            queue.put(datap);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static boolean isFinalMarker(DataPacket datap) {
        if (datap.data.hasArray()) {
            byte[] gBuffer = datap.data.array();
            if (gBuffer.length == 4) {
                if (gBuffer[0] == (byte) 0xAA && gBuffer[1] == (byte) 0xBB && gBuffer[2] == (byte) 0xCC && gBuffer[3] == (byte) 0xDD) {
                    return true;
                }
            }
        }
        return false;
    }
}
