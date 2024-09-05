package com.lmax.disruptor;

/**
 * Batch rewind strategy that always rewinds
 * 直接重试
 */
public class SimpleBatchRewindStrategy implements BatchRewindStrategy
{
    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        return RewindAction.REWIND;
    }
}
