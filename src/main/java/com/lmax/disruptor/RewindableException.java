package com.lmax.disruptor;

/**
 * A special exception that can be thrown while using the {@link BatchEventProcessor}.
 * On throwing this exception the {@link BatchEventProcessor} can choose to rewind and replay the batch or throw
 * depending on the {@link BatchRewindStrategy}
 * 使用BatchEventProcessor时可能抛出的特殊异常。在抛出此异常时，BatchEventProcessor可以根据batchrewind策略选择回滚并重放批处理或抛出
 */
public class RewindableException extends Throwable
{
    /**
     * @param cause The underlying cause of the exception.
     */
    public RewindableException(final Throwable cause)
    {
        super("REWINDING BATCH", cause);
    }
}
