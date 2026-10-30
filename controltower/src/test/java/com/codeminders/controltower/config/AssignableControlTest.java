package com.codeminders.controltower.config;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

import com.codeminders.ardrone.ARDrone.Animation;
import com.codeminders.ardrone.ARDrone.LED;
import com.codeminders.controltower.config.AssignableControl.Command;
import com.codeminders.controltower.config.AssignableControl.ControllerButton;

public class AssignableControlTest
{
    @Test
    public void parsesSimpleCommand()
    {
        AssignableControl c = new AssignableControl("START/TAKEOFF/0");

        assertEquals(ControllerButton.START, c.getButton());
        assertEquals(Command.TAKEOFF, c.getCommand());
        assertEquals(0, c.getDelay());
        assertEquals("START/TAKEOFF/0", c.getPrefString());
    }

    @Test
    public void parsesAnimation()
    {
        AssignableControl c = new AssignableControl("L1/PLAY_ANIMATION/250/WAVE/3");

        assertEquals(ControllerButton.L1, c.getButton());
        assertEquals(Command.PLAY_ANIMATION, c.getCommand());
        assertEquals(250, c.getDelay());
        assertEquals(Animation.WAVE, c.getAnim());
        assertEquals(3, c.getDuration());
    }

    @Test
    public void parsesLed()
    {
        AssignableControl c = new AssignableControl("R2/PLAY_LED/0/BLINK_RED/2.5/4");

        assertEquals(Command.PLAY_LED, c.getCommand());
        assertEquals(LED.BLINK_RED, c.getLed());
        assertEquals(2.5f, c.getFrequency(), 0f);
        assertEquals(4, c.getDuration());
    }

    @Test
    public void parsesRecordingPathWithEncodedSeparators()
    {
        AssignableControl c = new AssignableControl("SQUARE/TAKE_SNAPSHOT/0/home?user?pics");

        assertEquals(new File("home" + File.separator + "user" + File.separator + "pics"), c.getRecFile());
    }

    @Test
    public void animationRoundTripsThroughPrefString()
    {
        AssignableControl original = new AssignableControl(ControllerButton.CIRCLE, Animation.YAW_DANCE, 100, 5);
        AssignableControl restored = new AssignableControl(original.getPrefString());

        assertEquals(ControllerButton.CIRCLE, restored.getButton());
        assertEquals(Command.PLAY_ANIMATION, restored.getCommand());
        assertEquals(Animation.YAW_DANCE, restored.getAnim());
        assertEquals(100, restored.getDelay());
        assertEquals(5, restored.getDuration());
    }

    @Test
    public void ledRoundTripsThroughPrefString()
    {
        AssignableControl original = new AssignableControl(ControllerButton.TRIANGLE, LED.FIRE, 0, 1.5f, 2);
        AssignableControl restored = new AssignableControl(original.getPrefString());

        assertEquals(ControllerButton.TRIANGLE, restored.getButton());
        assertEquals(LED.FIRE, restored.getLed());
        assertEquals(1.5f, restored.getFrequency(), 0f);
        assertEquals(2, restored.getDuration());
    }

    @Test
    public void recordingPathRoundTripsThroughPrefString()
    {
        File dir = new File("videos" + File.separator + "drone");
        AssignableControl original = new AssignableControl(ControllerButton.CIRCLE, Command.RECORD_VIDEO, 0, dir);
        AssignableControl restored = new AssignableControl(original.getPrefString());

        assertEquals(Command.RECORD_VIDEO, restored.getCommand());
        assertEquals(dir, restored.getRecFile());
    }

    @Test
    public void constructorsSetButton()
    {
        assertEquals(ControllerButton.START,
                new AssignableControl(ControllerButton.START, Command.TAKEOFF, 0).getButton());
        assertEquals(ControllerButton.L1,
                new AssignableControl(ControllerButton.L1, Animation.WAVE, 0, 1).getButton());
        assertEquals(ControllerButton.R1,
                new AssignableControl(ControllerButton.R1, LED.FIRE, 0, 1f, 1).getButton());
        assertEquals(ControllerButton.SQUARE,
                new AssignableControl(ControllerButton.SQUARE, Command.TAKE_SNAPSHOT, 0, new File("pics")).getButton());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMalformedPrefString()
    {
        new AssignableControl("START/TAKEOFF");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownCommand()
    {
        new AssignableControl("START/FLY_TO_MOON/0");
    }
}
