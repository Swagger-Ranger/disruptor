/*
 * Copyright 2011 LMAX Ltd.
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

import java.util.concurrent.locks.LockSupport;

/**
 * Sleeping strategy that initially spins, then uses a Thread.yield(), and
 * eventually sleep (<code>LockSupport.parkNanos(n)</code>) for the minimum
 * number of nanos the OS and JVM will allow while the
 * {@link com.lmax.disruptor.EventProcessor}s are waiting on a barrier.
 *
 * <p>This strategy is a good compromise between performance and CPU resource.
 * Latency spikes can occur after quiet periods.  It will also reduce the impact
 * on the producing thread as it will not need signal any conditional variables
 * to wake up the event handling thread.
 * <p>
 * 睡眠策略，最初旋转，然后使用线程。yield()，最终实现sleep(锁支持。parkNanos(n))为事件处理器等待屏障时操作系统和JVM允许的最小nanos数。
 * 这种策略是性能和CPU资源之间的一个很好的折衷。潜伏期峰值可能出现在安静期之后。它还将减少对生产线程的影响，因为它不需要向任何条件变量发出信号来唤醒事件处理线程。
 */
public final class SleepingWaitStrategy implements WaitStrategy
{
    private static final int SPIN_THRESHOLD = 100;
    private static final int DEFAULT_RETRIES = 200;
    private static final long DEFAULT_SLEEP = 100;

    private final int retries;
    private final long sleepTimeNs;

    /**
     * Provides a sleeping wait strategy with the default retry and sleep settings
     */
    public SleepingWaitStrategy()
    {
        this(DEFAULT_RETRIES, DEFAULT_SLEEP);
    }

    /**
     * @param retries How many times the strategy should retry before sleeping
     */
    public SleepingWaitStrategy(final int retries)
    {
        this(retries, DEFAULT_SLEEP);
    }

    /**
     * @param retries How many times the strategy should retry before sleeping
     * @param sleepTimeNs How long the strategy should sleep, in nanoseconds
     */
    public SleepingWaitStrategy(final int retries, final long sleepTimeNs)
    {
        this.retries = retries;
        this.sleepTimeNs = sleepTimeNs;
    }

    @Override
    public long waitFor(
        final long sequence, final Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException
    {
        long availableSequence;
        int counter = retries;

        /*
         * 这里的dependentSequence，当有依赖时是去依赖的最小的sequence，如果没有就是当前ringBuffer的cursorSequence
         * 的处理逻辑都在ProcessingSequenceBarrier 里
         */
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            counter = applyWaitMethod(barrier, counter);
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
    }

    private int applyWaitMethod(final SequenceBarrier barrier, final int counter)
        throws AlertException
    {
        barrier.checkAlert();

        if (counter > SPIN_THRESHOLD)
        {
            return counter - 1;
        }
        else if (counter > 0)
        {
            /*
             * 如果经过了多次尝试还没有获取到，则尝试性的让出CPU时间，
             * Thread.yield()：提示调度器当前线程愿意放弃当前对处理器的使用。调度器可以忽略这个提示，Yield是一种启发式的尝试，旨在改善线程之间的相对进程，否则会过度使用CPU。
             */
            Thread.yield();
            return counter - 1;
        }
        else
        {
            // 这里睡眠结束后，代码会从这里睡眠的地方继续执行
            LockSupport.parkNanos(sleepTimeNs);
        }

        return counter;
    }
}
