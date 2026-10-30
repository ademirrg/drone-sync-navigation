package com.codeminders.ardrone.data.decoder.ardrone10.navdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.junit.Test;

import com.codeminders.ardrone.NavData;
import com.codeminders.ardrone.data.navdata.ControlAlgorithm;
import com.codeminders.ardrone.data.navdata.CtrlState;
import com.codeminders.ardrone.data.navdata.FlyingState;
import com.codeminders.ardrone.data.navdata.Mode;
import com.codeminders.ardrone.data.navdata.NavDataFormatException;
import com.codeminders.ardrone.data.navdata.vision.VisionTag;
import com.codeminders.ardrone.data.navdata.vision.VisionTag.VisionTagType;

public class ARDrone10NavDataTest
{
    private static final int HEADER         = 0x55667788;
    private static final int DEMO_TAG       = 0;
    private static final int VISION_TAG     = 16;
    private static final int CHECKSUM_TAG   = 0xFFFF;
    private static final int DEMO_LEN       = 148; // size of navdata_demo_t in the SDK
    private static final int VISION_LEN     = 4 + 4 + 6 * 4 * 4;

    /** Builds a little-endian navdata packet the way the drone sends it. */
    private static class PacketBuilder
    {
        private final ByteBuffer buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);

        PacketBuilder(int state, int sequence)
        {
            buf.putInt(HEADER).putInt(state).putInt(sequence).putInt(0 /* vision flag */);
        }

        PacketBuilder demo(int ctrlState, int battery, float pitch, float roll, float yaw, int altitudeMm, float vx,
                float vy, float vz)
        {
            int start = buf.position();
            buf.putShort((short) DEMO_TAG).putShort((short) DEMO_LEN);
            buf.putInt(ctrlState << 16).putInt(battery).putFloat(pitch).putFloat(roll).putFloat(yaw)
                    .putInt(altitudeMm).putFloat(vx).putFloat(vy).putFloat(vz);
            buf.position(start + DEMO_LEN);
            return this;
        }

        PacketBuilder visionTag(int type, int x, int y, int w, int h, int dist)
        {
            int start = buf.position();
            buf.putShort((short) VISION_TAG).putShort((short) VISION_LEN);
            int body = buf.position();
            buf.putInt(1); // nb_detected
            // Each field is an array of 4 ints; tag #0 is the first element of each
            int[] fields = { type, x, y, w, h, dist };
            for(int f = 0; f < fields.length; f++)
                buf.putInt(body + 4 + f * 16, fields[f]);
            buf.position(start + VISION_LEN);
            return this;
        }

        PacketBuilder option(int tag, int len)
        {
            int start = buf.position();
            buf.putShort((short) tag).putShort((short) len);
            buf.position(start + Math.max(len, 4));
            return this;
        }

        NavData parse() throws NavDataFormatException
        {
            option(CHECKSUM_TAG, 8);
            int len = buf.position();
            return ARDrone10NavData.createFromData(ByteBuffer.wrap(buf.array(), 0, len), len);
        }
    }

    @Test
    public void parsesDemoOption() throws Exception
    {
        NavData nd = new PacketBuilder(0, 42)
                .demo(3 /* FLYING */, 87, 1500f, -2500f, 90000f, 1234, 0.5f, -0.25f, 0.125f)
                .parse();

        assertEquals(Mode.DEMO, nd.getMode());
        assertEquals(42, nd.getSequence());
        assertEquals(CtrlState.FLYING, nd.getControlState());
        assertEquals(FlyingState.FLYING, nd.getFlyingState());
        assertEquals(87, nd.getBattery());
        assertEquals(1.5f, nd.getPitch(), 1e-4);   // millidegrees -> degrees
        assertEquals(-2.5f, nd.getRoll(), 1e-4);
        assertEquals(90f, nd.getYaw(), 1e-4);
        assertEquals(1.234f, nd.getAltitude(), 1e-4); // mm -> m
        assertEquals(0.5f, nd.getVx(), 1e-6);
        assertEquals(-0.25f, nd.getLongitude(), 1e-6);
        assertEquals(0.125f, nd.getVz(), 1e-6);
    }

    @Test
    public void withoutDemoOptionModeIsBootstrap() throws Exception
    {
        NavData nd = new PacketBuilder(0, 1).parse();
        assertEquals(Mode.BOOTSTRAP, nd.getMode());
    }

    @Test
    public void unknownOptionsAreSkipped() throws Exception
    {
        NavData nd = new PacketBuilder(0, 1)
                .option(1 /* TIME */, 8)
                .demo(2 /* LANDED */, 50, 0f, 0f, 0f, 0, 0f, 0f, 0f)
                .parse();

        assertEquals(Mode.DEMO, nd.getMode());
        assertEquals(FlyingState.LANDED, nd.getFlyingState());
        assertEquals(50, nd.getBattery());
    }

    @Test
    public void parsesStateBits() throws Exception
    {
        int state = 1             // flying
                | (1 << 1)        // video enabled
                | (1 << 3)        // angular speed control
                | (1 << 6)        // control command ACK
                | (1 << 15)       // battery too low
                | (1 << 30)       // communication problem
                | (1 << 31);      // emergency
        NavData nd = new PacketBuilder(state, 1).parse();

        assertTrue(nd.isFlying());
        assertTrue(nd.isVideoEnabled());
        assertFalse(nd.isVisionEnabled());
        assertEquals(ControlAlgorithm.ANGULAR_SPEED_CONTROL, nd.getControlAlgorithm());
        assertTrue(nd.isControlReceived());
        assertTrue(nd.isBatteryTooLow());
        assertFalse(nd.isBatteryTooHigh());
        assertTrue(nd.isCommunicationProblemOccurred());
        assertTrue(nd.isEmergency());
        assertFalse(nd.isMotorsDown());
    }

    @Test
    public void clearStateHasNoFlags() throws Exception
    {
        NavData nd = new PacketBuilder(0, 1).parse();

        assertFalse(nd.isFlying());
        assertFalse(nd.isEmergency());
        assertFalse(nd.isControlReceived());
        assertEquals(ControlAlgorithm.EULER_ANGELS_CONTROL, nd.getControlAlgorithm());
    }

    @Test
    public void parsesVisionTags() throws Exception
    {
        NavData nd = new PacketBuilder(0, 1).visionTag(4 /* COCARDE */, 500, 300, 40, 30, 150).parse();

        List<VisionTag> tags = nd.getVisionTags();
        assertEquals(1, tags.size());
        VisionTag t = tags.get(0);
        assertEquals(VisionTagType.CAD_TYPE_COCARDE, t.getType());
        assertEquals(500, t.getPosition().getX());
        assertEquals(300, t.getPosition().getY());
        assertEquals(40, t.getDimensions().getWidth());
        assertEquals(30, t.getDimensions().getHeight());
        assertEquals(150, t.getDistance());
    }

    @Test
    public void noVisionTagsWhenNoneDetected() throws Exception
    {
        assertNull(new PacketBuilder(0, 1).parse().getVisionTags());
    }

    @Test(expected = NavDataFormatException.class)
    public void rejectsBadHeader() throws Exception
    {
        ByteBuffer buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x12345678);
        ARDrone10NavData.createFromData(buf, 16);
    }

    @Test(expected = NavDataFormatException.class)
    public void rejectsZeroLengthOption() throws Exception
    {
        new PacketBuilder(0, 1).option(1, 0).parse();
    }

    @Test(expected = NavDataFormatException.class)
    public void rejectsInvalidControlState() throws Exception
    {
        new PacketBuilder(0, 1).demo(99, 0, 0f, 0f, 0f, 0, 0f, 0f, 0f).parse();
    }
}
