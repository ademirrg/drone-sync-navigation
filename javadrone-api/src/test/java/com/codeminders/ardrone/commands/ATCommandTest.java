package com.codeminders.ardrone.commands;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ATCommandTest
{
    // REF base value: bits 18, 20, 22, 24 and 28 are always set (SDK section 6.6)
    private static final int REF_BASE = 290717696;

    @Test
    public void takeOffSetsBit9()
    {
        assertEquals("AT*REF=1," + (REF_BASE | (1 << 9)) + "\r", new TakeOffCommand().getCommandString(1));
    }

    @Test
    public void landClearsBit9()
    {
        assertEquals("AT*REF=2," + REF_BASE + "\r", new LandCommand().getCommandString(2));
    }

    @Test
    public void emergencySetsBit8()
    {
        assertEquals("AT*REF=3," + (REF_BASE | (1 << 8)) + "\r", new EmergencyCommand().getCommandString(3));
    }

    @Test
    public void floatsAreSentAsIntBits()
    {
        // Example from the SDK developer guide: -0.8 is encoded as -1085485875
        String s = new MoveCommand(false, -0.8f, 0f, 0f, 0f).getCommandString(5);
        assertEquals("AT*PCMD=5,1,-1085485875,0,0,0\r", s);
    }

    @Test
    public void moveWithCombinedYawSetsModeBit1()
    {
        String s = new MoveCommand(true, 0f, 0f, 0f, 0f).getCommandString(1);
        assertEquals("AT*PCMD=1,3,0,0,0,0\r", s);
    }

    @Test
    public void moveEncodesAllFourAxesInOrder()
    {
        String s = new MoveCommand(false, 0.1f, 0.2f, 0.3f, 0.4f).getCommandString(1);
        assertEquals("AT*PCMD=1,1," + Float.floatToIntBits(0.1f) + "," + Float.floatToIntBits(0.2f) + ","
                + Float.floatToIntBits(0.3f) + "," + Float.floatToIntBits(0.4f) + "\r", s);
    }

    @Test
    public void hoverSendsAllZeros()
    {
        assertEquals("AT*PCMD=7,0,0,0,0,0\r", new HoverCommand().getCommandString(7));
    }

    @Test
    public void configureQuotesStrings()
    {
        assertEquals("AT*CONFIG=4,\"general:video_enable\",\"TRUE\"\r",
                new ConfigureCommand("general:video_enable", "TRUE").getCommandString(4));
    }

    @Test
    public void commandsWithoutParameters()
    {
        assertEquals("AT*FTRIM=1\r", new FlatTrimCommand().getCommandString(1));
        assertEquals("AT*COMWDG=2\r", new KeepAliveCommand().getCommandString(2));
    }

    @Test
    public void controlAnimationAndLedCommands()
    {
        assertEquals("AT*CTRL=1,5,0\r", new ControlCommand(5, 0).getCommandString(1));
        assertEquals("AT*ANIM=2,13,3\r", new PlayAnimationCommand(13, 3).getCommandString(2));
        assertEquals("AT*LED=3,2," + Float.floatToIntBits(2.0f) + ",4\r",
                new PlayLEDCommand(2, 2.0f, 4).getCommandString(3));
    }

    @Test
    public void packetIsAsciiCommandString() throws Exception
    {
        ATCommand cmd = new ConfigureCommand("a", "b");
        assertArrayEquals(cmd.getCommandString(9).getBytes("ASCII"), cmd.getPacket(9));
    }

    @Test
    public void equalityIgnoresSequenceNumber()
    {
        assertEquals(new ConfigureCommand("a", "1"), new ConfigureCommand("a", "1"));
        assertNotEquals(new ConfigureCommand("a", "1"), new ConfigureCommand("a", "2"));
        assertFalse(new FlatTrimCommand().equals(null));
    }

    @Test
    public void replacementRules()
    {
        assertTrue(new LandCommand().replaces(new TakeOffCommand()));
        assertTrue(new TakeOffCommand().replaces(new LandCommand()));
        assertTrue(new HoverCommand().replaces(new MoveCommand(true, 0.5f, 0f, 0f, 0f)));
        assertTrue(new MoveCommand(true, 0.1f, 0f, 0f, 0f).replaces(new MoveCommand(true, 0.2f, 0f, 0f, 0f)));
        assertFalse(new ConfigureCommand("a", "1").replaces(new ConfigureCommand("b", "1")));
        assertFalse(new MoveCommand(true, 0.1f, 0f, 0f, 0f).replaces(new TakeOffCommand()));
    }

    @Test
    public void prioritiesAndStickiness()
    {
        assertTrue(new QuitCommand().getPriority() > new KeepAliveCommand().getPriority());
        assertTrue(new KeepAliveCommand().getPriority() > new LandCommand().getPriority());
        assertTrue(new LandCommand().getPriority() > new MoveCommand(true, 0f, 0f, 0f, 0f).getPriority());

        assertTrue(new TakeOffCommand().isSticky());
        assertTrue(new LandCommand().isSticky());
        assertFalse(new MoveCommand(true, 0f, 0f, 0f, 0f).isSticky());
        assertNull(new FlatTrimCommand().getCategory());
    }
}
