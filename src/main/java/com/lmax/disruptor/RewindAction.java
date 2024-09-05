package com.lmax.disruptor;

/**
 * The result returned from the {@link BatchRewindStrategy} that decides whether to rewind or throw the exception
 */
public enum RewindAction
{
    /**
     * Rewind and replay the whole batch from  he beginning
     * 回滚，重新处理事件
     */
    REWIND,

    /**
     * rethrows the exception, delegating it to the configured {@link ExceptionHandler}
     * 抛出，中断事件处理流程
     */
    THROW
}
