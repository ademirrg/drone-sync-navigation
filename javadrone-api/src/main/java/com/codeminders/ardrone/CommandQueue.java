
package com.codeminders.ardrone;

import java.util.LinkedList;
import java.util.ListIterator;

import java.util.logging.Logger;

public class CommandQueue
{
    private LinkedList<DroneCommand> data;
    private int                      maxSize;
    private Logger                   log = Logger.getLogger(getClass().getName());

    public CommandQueue(int maxSize)
    {
        data = new LinkedList<DroneCommand>();
        this.maxSize = maxSize;
    }

    public DroneCommand take() throws InterruptedException
    {
        while(true)
        {
            DroneCommand res;
            synchronized(this)
            {
                while(data.isEmpty())
                {
                    // log.debug("Waiting for data");
                    wait();
                }
                res = data.removeLast();
                // log.debug("[" + data.size() + "] Returning " + res);
                if(!res.isSticky())
                    return res;

                int sc = res.incrementStickyCounter();
                data.addLast(res);
                if(sc <= 1)
                    return res;
            }

            // Sleep without holding the lock, so add() callers are not blocked
            // while a sticky command is being repeated
            Thread.sleep(res.getStickyRate());

            synchronized(this)
            {
                // It may have been cleared or replaced while we slept
                if(data.contains(res))
                    return res;
            }
        }
    }

    public synchronized void add(DroneCommand cmd)
    {
        // Replace a pending command anywhere in the queue, keeping its position
        ListIterator<DroneCommand> i = data.listIterator();
        while(i.hasNext())
        {
            if(i.next().replaces(cmd))
            {
                // log.debug("Replacing duplicate element " + cmd);
                i.set(cmd);
                return;
            }
        }

        // Otherwise insert before the first command with the same or higher priority
        int p = cmd.getPriority();
        i = data.listIterator();
        while(i.hasNext())
        {
            if(i.next().getPriority() >= p)
            {
                i.previous();
                break;
            }
        }
        // log.debug("[" + data.size() + "] Adding command " + cmd);
        i.add(cmd);
        notify();

        while(data.size() > maxSize)
        {
            if(!dropOne())
                break;
        }
    }

    /**
     * Drops the oldest non-sticky command with the lowest priority.
     *
     * @return false if there was nothing that could be dropped
     */
    private boolean dropOne()
    {
        int victim = -1;
        int victimPriority = Integer.MAX_VALUE;
        int pos = 0;
        for(DroneCommand x : data)
        {
            // Newer commands sit before older ones of equal priority, so on a tie
            // the later position is the older command
            if(!x.isSticky() && x.getPriority() <= victimPriority)
            {
                victim = pos;
                victimPriority = x.getPriority();
            }
            pos++;
        }
        if(victim < 0)
            return false;

        log.fine("Queue over " + maxSize + " commands, dropping " + data.get(victim));
        data.remove(victim);
        return true;
    }

    public synchronized int size()
    {
        return data.size();
    }

    public synchronized void clear()
    {
        data.clear();
        notify();
    }

}
