package com.lmax.disruptor;

/**
 * Strategy for handling a rewindableException when processing an event.
 * 定义在事件处理过程遇到{@link  RewindableException}的处理策略，{@link RewindHandler}是负责执行这一策略的核心类
 *
 */
public interface BatchRewindStrategy
{

    /**
     * When a {@link RewindableException} is thrown, this will be called.
     *
     * @param e       the exception that propagated from the {@link EventHandler}.
     * @param attempts how many attempts there have been for the batch
     * @return the decision of whether to rewind the batch or throw the exception
     */
    RewindAction handleRewindException(RewindableException e, int attempts);
}
