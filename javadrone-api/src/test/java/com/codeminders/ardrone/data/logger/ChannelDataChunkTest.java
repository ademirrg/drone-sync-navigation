package com.codeminders.ardrone.data.logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;

import org.junit.Test;

public class ChannelDataChunkTest
{
    @Test
    public void roundTripsThroughStream() throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        new ChannelDataChunk(new byte[] { 1, 2, 3 }, 1500L).writeToStream(out);
        new ChannelDataChunk(new byte[0], 7L).writeToStream(out);
        out.flush();

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        ChannelDataChunk a = ChannelDataChunk.readFromStream(in);
        ChannelDataChunk b = ChannelDataChunk.readFromStream(in);

        assertArrayEquals(new byte[] { 1, 2, 3 }, a.getData());
        assertEquals(1500L, a.getIoDelay());
        assertEquals(0, b.getData().length);
        assertEquals(7L, b.getIoDelay());
    }

    @Test(expected = EOFException.class)
    public void truncatedStreamFails() throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ChannelDataChunk(new byte[] { 1, 2, 3, 4 }, 1L).writeToStream(new DataOutputStream(bos));
        byte[] bytes = bos.toByteArray();
        byte[] truncated = new byte[bytes.length - 2];
        System.arraycopy(bytes, 0, truncated, 0, truncated.length);

        ChannelDataChunk.readFromStream(new DataInputStream(new ByteArrayInputStream(truncated)));
    }
}
