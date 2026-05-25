package org.jztrmnkl.filterservice.model;

import java.util.concurrent.atomic.AtomicLong;

public class ProcessingStats {

    private final AtomicLong received      = new AtomicLong(0);
    private final AtomicLong exactDups     = new AtomicLong(0);
    private final AtomicLong nearDups      = new AtomicLong(0);
    private final AtomicLong bots          = new AtomicLong(0);
    private final AtomicLong shipped       = new AtomicLong(0);

    public void incrementReceived()   { received.incrementAndGet(); }
    public void incrementExactDups()  { exactDups.incrementAndGet(); }
    public void incrementNearDups()   { nearDups.incrementAndGet(); }
    public void incrementBots()       { bots.incrementAndGet(); }
    public void incrementShipped()    { shipped.incrementAndGet(); }

    public long getReceived()   { return received.get(); }
    public long getExactDups()  { return exactDups.get(); }
    public long getNearDups()   { return nearDups.get(); }
    public long getBots()       { return bots.get(); }
    public long getShipped()    { return shipped.get(); }
}
