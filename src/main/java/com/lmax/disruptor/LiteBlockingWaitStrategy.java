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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Variation of the {@link BlockingWaitStrategy} that attempts to elide conditional wake-ups when
 * the lock is uncontended.  Shows performance improvements on microbenchmarks.  However this
 * wait strategy should be considered experimental as I have not full proved the correctness of
 * the lock elision code.
 */
public final class LiteBlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();
    private final AtomicBoolean signalNeeded = new AtomicBoolean(false);

    @Override
    public long waitFor(final long sequence, final Sequence cursorSequence, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        if (cursorSequence.get() < sequence)
        {
            synchronized (mutex)
            {
                do
                {
                    signalNeeded.getAndSet(true);

                    if (cursorSequence.get() >= sequence)
                    {
                        break;
                    }

                    barrier.checkAlert();
                    mutex.wait();
                }
                while (cursorSequence.get() < sequence);
            }
        }

        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            Thread.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        /*
         * LiteBlockingWaitStrategy和BlockingWaitStrategy都是使用的synchronized来处理消费者同步等待的
         * 只是LiteBlockingWaitStrategy使用了一个atomicBoolean来避免notifyAll唤醒的时候出现惊群效应，
         * 因为当生产者有一个数据就绪时就会立马唤醒所有线程，此时必然是少量线程能成功，而大量的线程去竞争同一把锁，会增加不必要的上下文切换和 CPU 开销。
         */
        if (signalNeeded.getAndSet(false))
        {
            synchronized (mutex)
            {
                mutex.notifyAll();
            }
        }
    }

    @Override
    public String toString()
    {
        return "LiteBlockingWaitStrategy{" +
            "mutex=" + mutex +
            ", signalNeeded=" + signalNeeded +
            '}';
    }
}
