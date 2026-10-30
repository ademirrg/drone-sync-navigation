package com.codeminders.ardrone.data.navdata;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavDataEnumsTest
{
    @Test
    public void ctrlStateFromInt() throws Exception
    {
        CtrlState[] expected = { CtrlState.DEFAULT, CtrlState.INIT, CtrlState.LANDED, CtrlState.FLYING,
                CtrlState.HOVERING, CtrlState.TEST, CtrlState.TRANS_TAKEOFF, CtrlState.TRANS_GOTOFIX,
                CtrlState.TRANS_LANDING };
        for(int i = 0; i < expected.length; i++)
            assertEquals(expected[i], CtrlState.fromInt(i));
    }

    @Test(expected = NavDataFormatException.class)
    public void ctrlStateRejectsUnknownValue() throws Exception
    {
        CtrlState.fromInt(9);
    }

    @Test
    public void flyingStateFromControlState()
    {
        assertEquals(FlyingState.FLYING, FlyingState.fromControlState(CtrlState.FLYING));
        assertEquals(FlyingState.FLYING, FlyingState.fromControlState(CtrlState.HOVERING));
        assertEquals(FlyingState.FLYING, FlyingState.fromControlState(CtrlState.TRANS_GOTOFIX));
        assertEquals(FlyingState.TAKING_OFF, FlyingState.fromControlState(CtrlState.TRANS_TAKEOFF));
        assertEquals(FlyingState.LANDING, FlyingState.fromControlState(CtrlState.TRANS_LANDING));
        assertEquals(FlyingState.LANDED, FlyingState.fromControlState(CtrlState.LANDED));
        assertEquals(FlyingState.LANDED, FlyingState.fromControlState(CtrlState.DEFAULT));
        assertEquals(FlyingState.LANDED, FlyingState.fromControlState(CtrlState.INIT));
    }
}
