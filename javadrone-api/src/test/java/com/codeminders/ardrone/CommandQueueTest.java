package com.codeminders.ardrone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.codeminders.ardrone.commands.ConfigureCommand;
import com.codeminders.ardrone.commands.FlatTrimCommand;
import com.codeminders.ardrone.commands.HoverCommand;
import com.codeminders.ardrone.commands.KeepAliveCommand;
import com.codeminders.ardrone.commands.LandCommand;
import com.codeminders.ardrone.commands.MoveCommand;
import com.codeminders.ardrone.commands.QuitCommand;
import com.codeminders.ardrone.commands.TakeOffCommand;

public class CommandQueueTest
{
    private static List<DroneCommand> drain(CommandQueue q, int n) throws InterruptedException
    {
        List<DroneCommand> res = new ArrayList<DroneCommand>();
        for(int i = 0; i < n; i++)
            res.add(q.take());
        return res;
    }

    @Test
    public void equalPriorityIsFifo() throws Exception
    {
        CommandQueue q = new CommandQueue(64);
        ConfigureCommand first = new ConfigureCommand("first", "1");
        ConfigureCommand second = new ConfigureCommand("second", "2");
        q.add(first);
        q.add(second);

        assertSame(first, q.take());
        assertSame(second, q.take());
    }

    @Test
    public void higherPriorityIsSentFirst() throws Exception
    {
        CommandQueue q = new CommandQueue(64);
        ConfigureCommand config = new ConfigureCommand("a", "1");
        KeepAliveCommand keepAlive = new KeepAliveCommand();
        QuitCommand quit = new QuitCommand();
        q.add(config);
        q.add(keepAlive);
        q.add(quit);

        List<DroneCommand> sent = drain(q, 3);
        assertSame(quit, sent.get(0));
        assertSame(keepAlive, sent.get(1));
        assertSame(config, sent.get(2));
    }

    @Test
    public void identicalCommandIsNotDuplicated()
    {
        CommandQueue q = new CommandQueue(64);
        q.add(new ConfigureCommand("a", "1"));
        q.add(new ConfigureCommand("a", "1"));

        assertEquals(1, q.size());
    }

    @Test
    public void movesCollapseToLatestEvenBehindOtherCommands() throws Exception
    {
        CommandQueue q = new CommandQueue(64);
        q.add(new ConfigureCommand("a", "1"));
        for(int i = 0; i < 500; i++)
            q.add(new MoveCommand(true, i / 1000f, 0f, 0f, 0f));
        MoveCommand latest = new MoveCommand(true, 0.9f, 0f, 0f, 0f);
        q.add(latest);
        q.add(new ConfigureCommand("b", "2"));

        assertEquals(3, q.size());
        List<DroneCommand> sent = drain(q, 3);
        assertSame(latest, sent.get(1)); // keeps the slot of the first move
    }

    @Test
    public void hoverReplacesPendingMove() throws Exception
    {
        CommandQueue q = new CommandQueue(64);
        q.add(new MoveCommand(true, 0.5f, 0f, 0f, 0f));
        HoverCommand hover = new HoverCommand();
        q.add(hover);

        assertEquals(1, q.size());
        assertSame(hover, q.take());
    }

    @Test
    public void landReplacesPendingTakeOff() throws Exception
    {
        CommandQueue q = new CommandQueue(64);
        q.add(new TakeOffCommand());
        q.take(); // first send; sticky command goes back to the tail
        LandCommand land = new LandCommand();
        q.add(land);

        assertEquals(1, q.size());
        assertSame(land, q.take());
    }

    @Test
    public void stickyCommandIsRepeated() throws Exception
    {
        CommandQueue q = new CommandQueue(64);
        TakeOffCommand takeOff = new TakeOffCommand();
        q.add(takeOff);

        assertSame(takeOff, q.take());
        assertSame(takeOff, q.take());
        assertEquals(1, q.size());
        assertEquals(2, takeOff.getStickyCounter());
    }

    @Test
    public void sizeLimitDropsOldestLowestPriority() throws Exception
    {
        CommandQueue q = new CommandQueue(4);
        q.add(new KeepAliveCommand());
        for(int i = 0; i < 10; i++)
            q.add(new ConfigureCommand("k" + i, "v"));

        assertEquals(4, q.size());
        List<DroneCommand> sent = drain(q, 4);
        assertTrue(sent.get(0) instanceof KeepAliveCommand);
        assertEquals(new ConfigureCommand("k7", "v"), sent.get(1));
        assertEquals(new ConfigureCommand("k8", "v"), sent.get(2));
        assertEquals(new ConfigureCommand("k9", "v"), sent.get(3));
    }

    @Test
    public void sizeLimitNeverDropsStickyCommands() throws Exception
    {
        CommandQueue q = new CommandQueue(2);
        TakeOffCommand takeOff = new TakeOffCommand();
        q.add(takeOff);
        for(int i = 0; i < 10; i++)
            q.add(new ConfigureCommand("k" + i, "v"));

        assertEquals(2, q.size());
        assertSame(takeOff, q.take());
    }

    @Test(timeout = 5000)
    public void takeBlocksUntilCommandAdded() throws Exception
    {
        final CommandQueue q = new CommandQueue(64);
        final DroneCommand[] got = new DroneCommand[1];
        Thread t = new Thread()
        {
            public void run()
            {
                try
                {
                    got[0] = q.take();
                } catch(InterruptedException e)
                {
                }
            }
        };
        t.start();
        Thread.sleep(50);
        FlatTrimCommand trim = new FlatTrimCommand();
        q.add(trim);
        t.join();

        assertSame(trim, got[0]);
    }

    @Test(timeout = 5000)
    public void addIsNotBlockedWhileStickyCommandWaits() throws Exception
    {
        final CommandQueue q = new CommandQueue(64);
        q.add(new TakeOffCommand());
        q.take();
        Thread sender = new Thread()
        {
            public void run()
            {
                try
                {
                    q.take(); // sleeps for the sticky rate
                } catch(InterruptedException e)
                {
                }
            }
        };
        sender.start();
        Thread.sleep(20);

        long start = System.nanoTime();
        q.add(new ConfigureCommand("x", "y"));
        long elapsedMs = (System.nanoTime() - start) / 1000000;
        sender.join();

        assertTrue("add() took " + elapsedMs + " ms", elapsedMs < 50);
    }

    @Test(timeout = 5000)
    public void clearedStickyCommandIsNotResent() throws Exception
    {
        final CommandQueue q = new CommandQueue(64);
        q.add(new TakeOffCommand());
        q.take();
        final DroneCommand[] got = new DroneCommand[1];
        Thread sender = new Thread()
        {
            public void run()
            {
                try
                {
                    got[0] = q.take();
                } catch(InterruptedException e)
                {
                }
            }
        };
        sender.start();
        Thread.sleep(20);
        q.clear();
        FlatTrimCommand trim = new FlatTrimCommand();
        q.add(trim);
        sender.join();

        assertSame(trim, got[0]);
    }
}
