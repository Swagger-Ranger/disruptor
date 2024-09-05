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

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p> 使用synchronized阻塞式的等待机制实现等待，直到数据可用。
 * <p> 优点:当系统负载较低时，能够有效地节省 CPU 资源，因为线程在等待时会被挂起，避免了繁重的自旋等待。
 * <p> 缺点:可能导致较高的线程上下文切换开销，尤其是在高负载或高频率的情况下。
 * <p> 适用场景:适合于对延迟不敏感且需要节省 CPU 资源的场景。
 *
 * <p>This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();

    @Override
    public long waitFor(final long sequence, final Sequence cursorSequence, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        // 这里在Disruptor中sequence是会全局递增一直累加的，而且此处是只有生产者根不上消费者时才阻塞，否则就直接返回安全的sequence序号
        if (cursorSequence.get() < sequence)
        {
            synchronized (mutex)
            {
                while (cursorSequence.get() < sequence)
                {
                    barrier.checkAlert();
                    mutex.wait();
                }
            }
        }

        // 这里就是在处理消费者之间有依赖的情况
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            // Thread.onSpinWait() 是 Java 9 引入的一个方法，主要用于在自旋等待（spin-waiting）期间通知JVM线程正在进行忙等待。有助于JVM优化线程调度和处理器资源的分配。
            Thread.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        synchronized (mutex)
        {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}
