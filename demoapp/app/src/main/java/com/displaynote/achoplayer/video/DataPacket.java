package com.displaynote.achoplayer.video;

import java.nio.ByteBuffer;

public class DataPacket {
    public ByteBuffer data;
    public int data_len;
    public double ts;
    public double base_ts;
    public double duration;
    public long completion_id;
    boolean is_header;
    boolean is_mirror;
}
