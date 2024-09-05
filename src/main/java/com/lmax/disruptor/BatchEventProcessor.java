/*
 * Copyright 2022 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicInteger;

import static com.lmax.disruptor.RewindAction.REWIND;
import static java.lang.Math.min;


/**
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available events to an {@link EventHandler}.
 *  EventProcessor 的核心实现类，控制整体Disruptor消费流程的核心类
 *  EventProcessor和event Handler一对一，和sequence也是一对一，并且同一个RingBuffer里面是可以启动多个EventProcessor
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class BatchEventProcessor<T>
        implements EventProcessor
{
    private static final int IDLE = 0;
    private static final int HALTED = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    private final AtomicInteger running = new AtomicInteger(IDLE);
    private ExceptionHandler<? super T> exceptionHandler;
    private final DataProvider<T> dataProvider;
    private final SequenceBarrier sequenceBarrier;
    private final EventHandlerBase<? super T> eventHandler;
    private final int batchLimitOffset;
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RewindHandler rewindHandler;
    private int retriesAttempted = 0;

    BatchEventProcessor(
            final DataProvider<T> dataProvider,
            final SequenceBarrier sequenceBarrier,
            final EventHandlerBase<? super T> eventHandler,
            final int maxBatchSize,
            final BatchRewindStrategy batchRewindStrategy
    )
    {
        this.dataProvider = dataProvider;
        this.sequenceBarrier = sequenceBarrier;
        this.eventHandler = eventHandler;

        if (maxBatchSize < 1)
        {
            throw new IllegalArgumentException("maxBatchSize must be greater than 0");
        }
        this.batchLimitOffset = maxBatchSize - 1;

        this.rewindHandler = eventHandler instanceof RewindableEventHandler
                ? new TryRewindHandler(batchRewindStrategy)
                : new NoRewindHandler();
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running.set(HALTED);
        sequenceBarrier.alert();
    }

    @Override
    public boolean isRunning()
    {
        return running.get() != IDLE;
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEventProcessor}.
     *
     * @param exceptionHandler to replace the existing exceptionHandler.
     */
    public void setExceptionHandler(final ExceptionHandler<? super T> exceptionHandler)
    {
        if (null == exceptionHandler)
        {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
    }

    /**
     * It is ok to have another thread rerun this method after a halt().
     *
     * @throws IllegalStateException if this object instance is already running in a thread
     */
    @Override
    public void run()
    {
        int witnessValue = running.compareAndExchange(IDLE, RUNNING);
        // compareAndExchange，返回的实际上就是原有的值，如果原有的值==expectedValue则设置成功，如果!=expectedValue则设置失败。
        if (witnessValue == IDLE) // Successful CAS
        {
            sequenceBarrier.clearAlert();

            notifyStart();
            try
            {
                if (running.get() == RUNNING)
                {
                    processEvents();
                }
            }
            finally
            {
                notifyShutdown();
                running.set(IDLE);
            }
        }
        else
        {
            if (witnessValue == RUNNING)
            {
                throw new IllegalStateException("Thread is already running");
            }
            else
            {
                earlyExit();
            }
        }
    }

    /**
     *  BatchEventProcessor 处理事件的核心循环。不断地从 RingBuffer 中获取事件，并交给 EventHandler 处理，同时处理异常、重试机制和超时等情况。
     *  消费者消费数据都是通过WaitStrategy和生产者协调，并且通过自己维护的Sequence来记录自己的位置，每个消费者都是独立的Sequence，而且是一对一的，
     *  并在final long availableSequence = sequenceBarrier.waitFor(nextSequence);中不断的获取当前可用的和批处理数量限制之间的最小值来处理数据的。
     *  <p>
     *  在4.0的Disruptor中移除了WorkerPool，也就是没有多个线程者来共享一个Sequence。（disruptor 移除 WorkerPool我理解还是基于性能的考虑，
     *  因为WorkerPool中多消费者共享一个Sequence必然要产生竞争存在同步消耗，而只有一个线程则不用同步sequence，
     *  如果要多个线程提升并行度那可以在自己的eventHandler中使用ForkJoin之类的来处理也是一样的这样也可以并行消费而且还没有同步sequence消耗。）
     *  <pre>{@code
     *     class MyEventHandler implements EventHandler<MyEvent> {
     *     private final ForkJoinPool forkJoinPool = new ForkJoinPool();
     *
     *     @Override
     *     public void onEvent(MyEvent event, long sequence, boolean endOfBatch) {
     *         // ForkJoinPool 用于并行处理事件
     *         forkJoinPool.submit(() -> {
     *             // 执行事件处理逻辑
     *             System.out.println("Processing event: " + event.getData());
     *         }).join();
     *     }
     * }
     *  }
     */
    private void processEvents()
    {
        T event = null;
        long nextSequence = sequence.get() + 1L;

        while (true)
        {
            final long startOfBatchSequence = nextSequence;
            try
            {
                try
                {
                    final long availableSequence = sequenceBarrier.waitFor(nextSequence);
                    final long endOfBatchSequence = min(nextSequence + batchLimitOffset, availableSequence);

                    if (nextSequence <= endOfBatchSequence)
                    {
                        eventHandler.onBatchStart(endOfBatchSequence - nextSequence + 1, availableSequence - nextSequence + 1);
                    }

                    while (nextSequence <= endOfBatchSequence)
                    {
                        event = dataProvider.get(nextSequence);
                        // 这里数据已经通过event = dataProvider.get(nextSequence);取出来了，
                        // 此时这个onEvent方法就是在执行消费逻辑，而sequence是告诉处理逻辑RingBuffer中的位置，endOfBatch是否是此批量的最后一个
                        eventHandler.onEvent(event, nextSequence, nextSequence == endOfBatchSequence);
                        nextSequence++;
                    }

                    retriesAttempted = 0;
                    // 推进自己的 sequence
                    sequence.set(endOfBatchSequence);
                }
                catch (final RewindableException e)
                {
                    // 这里就是 RewindableException异常的处理，将游标作回滚，是否回滚看构造器中传入的是否是 RewindableEventHandler
                    nextSequence = rewindHandler.attemptRewindGetNextSequence(e, startOfBatchSequence);
                }
            }
            catch (final TimeoutException e)
            {
                notifyTimeout(sequence.get());
            }
            catch (final AlertException ex)
            {
                if (running.get() != RUNNING)
                {
                    break;
                }
            }
            catch (final Throwable ex)
            {
                handleEventException(ex, nextSequence, event);
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    private void earlyExit()
    {
        notifyStart();
        notifyShutdown();
    }

    private void notifyTimeout(final long availableSequence)
    {
        try
        {
            eventHandler.onTimeout(availableSequence);
        }
        catch (Throwable e)
        {
            handleEventException(e, availableSequence, null);
        }
    }

    /**
     * Notifies the EventHandler when this processor is starting up.
     */
    private void notifyStart()
    {
        try
        {
            eventHandler.onStart();
        }
        catch (final Throwable ex)
        {
            handleOnStartException(ex);
        }
    }

    /**
     * Notifies the EventHandler immediately prior to this processor shutting down.
     */
    private void notifyShutdown()
    {
        try
        {
            eventHandler.onShutdown();
        }
        catch (final Throwable ex)
        {
            handleOnShutdownException(ex);
        }
    }

    /**
     * Delegate to {@link ExceptionHandler#handleEventException(Throwable, long, Object)} on the delegate or
     * the default {@link ExceptionHandler} if one has not been configured.
     */
    private void handleEventException(final Throwable ex, final long sequence, final T event)
    {
        getExceptionHandler().handleEventException(ex, sequence, event);
    }

    /**
     * Delegate to {@link ExceptionHandler#handleOnStartException(Throwable)} on the delegate or
     * the default {@link ExceptionHandler} if one has not been configured.
     */
    private void handleOnStartException(final Throwable ex)
    {
        getExceptionHandler().handleOnStartException(ex);
    }

    /**
     * Delegate to {@link ExceptionHandler#handleOnShutdownException(Throwable)} on the delegate or
     * the default {@link ExceptionHandler} if one has not been configured.
     */
    private void handleOnShutdownException(final Throwable ex)
    {
        getExceptionHandler().handleOnShutdownException(ex);
    }

    private ExceptionHandler<? super T> getExceptionHandler()
    {
        ExceptionHandler<? super T> handler = exceptionHandler;
        return handler == null ? ExceptionHandlers.defaultHandler() : handler;
    }

    private class TryRewindHandler implements RewindHandler
    {
        private final BatchRewindStrategy batchRewindStrategy;

        TryRewindHandler(final BatchRewindStrategy batchRewindStrategy)
        {
            this.batchRewindStrategy = batchRewindStrategy;
        }

        @Override
        public long attemptRewindGetNextSequence(final RewindableException e, final long startOfBatchSequence) throws RewindableException
        {
            if (batchRewindStrategy.handleRewindException(e, ++retriesAttempted) == REWIND)
            {
                return startOfBatchSequence;
            }
            else
            {
                retriesAttempted = 0;
                throw e;
            }
        }
    }

    private static class NoRewindHandler implements RewindHandler
    {
        @Override
        public long attemptRewindGetNextSequence(final RewindableException e, final long startOfBatchSequence)
        {
            throw new UnsupportedOperationException("Rewindable Exception thrown from a non-rewindable event handler", e);
        }
    }
}