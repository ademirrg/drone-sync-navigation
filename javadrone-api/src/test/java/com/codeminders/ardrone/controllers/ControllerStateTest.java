package com.codeminders.ardrone.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.codeminders.ardrone.controllers.decoders.SonyPS3ControllerStateDecoder;

public class ControllerStateTest
{
    private static GameControllerState state(boolean start, boolean cross, int leftX, int rightY)
    {
        return new GameControllerState(false, cross, false, false, false, false, false, false, false, start, false,
                false, false, 0, 0, leftX, 0, 0, rightY);
    }

    @Test
    public void detectsButtonPress()
    {
        ControllerStateChange c = new ControllerStateChange(state(false, false, 0, 0), state(true, false, 0, 0));

        assertTrue(c.isStartChanged());
        assertTrue(c.isStart());
        assertFalse(c.isCrossChanged());
        assertTrue(c.isButtonStateChanged());
        assertTrue(c.isChanged());
        assertFalse(c.isJoysticksChanged());
    }

    @Test
    public void detectsButtonRelease()
    {
        ControllerStateChange c = new ControllerStateChange(state(false, true, 0, 0), state(false, false, 0, 0));

        assertTrue(c.isCrossChanged());
        assertFalse(c.isCross());
    }

    @Test
    public void reportsJoystickDeltas()
    {
        ControllerStateChange c = new ControllerStateChange(state(false, false, 10, -20), state(false, false, 50, 30));

        assertEquals(40, c.getLeftJoystickXChange());
        assertEquals(50, c.getRightJoystickYChange());
        assertTrue(c.isLeftJoystickChanged());
        assertTrue(c.isRightJoystickChanged());
        assertTrue(c.isJoysticksChanged());
        assertFalse(c.isButtonStateChanged());
    }

    @Test
    public void nullOldStateMeansNoChange()
    {
        ControllerStateChange c = new ControllerStateChange(null, state(true, true, 100, 100));

        assertFalse(c.isChanged());
        assertTrue(c.isStart());
        assertEquals(100, c.getLeftJoystickX());
    }

    @Test
    public void ps3DecoderReadsButtonsAndSticks() throws Exception
    {
        byte[] report = new byte[49];
        report[2] = (byte) ((1 << 0) | (1 << 3)); // select, start
        report[3] = (byte) ((1 << 4) | (1 << 6)); // triangle, cross
        report[4] = (byte) 1;                     // PS
        report[6] = (byte) 0;                     // left X: full left
        report[7] = (byte) 128;                   // left Y: centre
        report[8] = (byte) 255;                   // right X: full right
        report[9] = (byte) 200;                   // right Y

        GameControllerState s = new SonyPS3ControllerStateDecoder().decodeState(new ControllerData(report,
                report.length));

        assertTrue(s.isSelect());
        assertTrue(s.isStart());
        assertTrue(s.isTriangle());
        assertTrue(s.isCross());
        assertTrue(s.isPS());
        assertFalse(s.isCircle());
        assertFalse(s.isSquare());
        assertEquals(-128, s.getLeftJoystickX());
        assertEquals(0, s.getLeftJoystickY());
        assertEquals(127, s.getRightJoystickX());
        assertEquals(72, s.getRightJoystickY());
    }
}
