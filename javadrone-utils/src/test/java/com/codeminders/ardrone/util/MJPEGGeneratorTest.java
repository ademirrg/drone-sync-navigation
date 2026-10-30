package com.codeminders.ardrone.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MJPEGGeneratorTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void swapsIntByteOrder()
    {
        assertEquals(0x78563412, MJPEGGenerator.swapInt(0x12345678));
        assertEquals(0x000000FF, MJPEGGenerator.swapInt(0xFF000000));
        assertEquals(0x12345678, MJPEGGenerator.swapInt(MJPEGGenerator.swapInt(0x12345678)));
    }

    @Test
    public void swapsShortByteOrder()
    {
        assertEquals((short) 0x3412, MJPEGGenerator.swapShort((short) 0x1234));
        assertEquals((short) 0x1800, MJPEGGenerator.swapShort((short) 24));
    }

    @Test
    public void writesBigEndianBytes()
    {
        assertArrayEquals(new byte[] { 0x12, 0x34, 0x56, 0x78 }, MJPEGGenerator.intBytes(0x12345678));
        assertArrayEquals(new byte[] { 0x12, 0x34 }, MJPEGGenerator.shortBytes((short) 0x1234));
    }

    @Test
    public void writesAviFile() throws Exception
    {
        File avi = new File(tmp.getRoot(), "test.avi");
        MJPEGGenerator gen = new MJPEGGenerator(avi, 32, 24, 10.0, 0);
        for(int i = 0; i < 3; i++)
            gen.addImage(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB));
        gen.finishAVI();

        assertTrue(avi.length() > 0);
        byte[] magic = new byte[12];
        DataInputStream in = new DataInputStream(new FileInputStream(avi));
        try
        {
            in.readFully(magic);
        } finally
        {
            in.close();
        }
        assertEquals("RIFF", new String(magic, 0, 4, "ASCII"));
        assertEquals("AVI ", new String(magic, 8, 4, "ASCII"));
        // RIFF size field is little-endian and covers everything after the first 8 bytes
        int riffSize = (magic[4] & 0xFF) | (magic[5] & 0xFF) << 8 | (magic[6] & 0xFF) << 16 | (magic[7] & 0xFF) << 24;
        assertEquals(avi.length() - 8, riffSize);
    }
}
