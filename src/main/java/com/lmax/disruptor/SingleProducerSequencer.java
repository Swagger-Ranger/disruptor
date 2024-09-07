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

import com.lmax.disruptor.util.Util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

abstract class SingleProducerSequencerPad extends AbstractSequencer
{
    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;

    SingleProducerSequencerPad(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }
}

abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad
{
    SingleProducerSequencerFields(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * Set to -1 as sequence starting point
     * nextValue 和 cachedValue 是 SingleProducerSequencer 中非常核心的两个变量，涉及到生产者的位置追踪和消费者进度的缓存。为了避免伪共享，两个被填充在中间
     */
    // nextValue 表示当前生产者即将发布的下一个序号
    long nextValue = Sequence.INITIAL_VALUE;
    // 上次从消费者中获取的最小序号，用于优化性能，避免每次都查询所有消费者的进度。
    long cachedValue = Sequence.INITIAL_VALUE;
}

/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Not safe for use from multiple threads as it does not implement any barriers.
 *
 * <p>* Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#publish(long)} is made.
 */

public final class SingleProducerSequencer extends SingleProducerSequencerFields
{
    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public SingleProducerSequencer(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(final int requiredCapacity, final boolean doStore)
    {

        long nextValue = this.nextValue;
        // wrapPoint 是一个计算值，用于判断是否有足够的容量，如果生产者发布requiredCapacity的事件，它的序号会“绕回”到 RingBuffer 的哪一位置。
        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        // cachedGatingSequence 是上次从消费者中获取的最小序号
        long cachedGatingSequence = this.cachedValue;

        /*
         * 这里的if逻辑满足则说明需要进一步检查是否有足够的可用空间
         * 如果 wrapPoint 大于 cachedGatingSequence，说明生产者可能会覆盖尚未被消费者处理的事件，需要进一步验证是否有足够的容量
         * 如果 cachedGatingSequence 大于 nextValue，说明消费者的进度快于生产者的位置。一般不可能，除非出问题，但也需要检查。
         */
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            //doStore就是是否要加入缓存屏障，在单线程的情况下nextValue始终是一致的，只有自己会修改，而调用setVolatile，是为了使用其方法中的内存屏障代码
            if (doStore)
            {
                cursor.setVolatile(nextValue);  // StoreLoad fence
            }

            // 这里重新计算 minSequence的位置，并且更新缓存值
            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            this.cachedValue = minSequence;

            // 如果 wrapPoint > minSequence 则才是false空间不足，否则都是外层的true即空间足够
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#next()
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     * @see Sequencer#next(int)
     */
    @Override
    public long next(final int n)
    {
        // 在生产环境中通常不启用断言。开启参数：java -ea
        assert sameThread() : "Accessed by two threads - use ProducerType.MULTI!";

        if (n < 1 || n > bufferSize)
        {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        long nextValue = this.nextValue;

        long nextSequence = nextValue + n;
        long wrapPoint = nextSequence - bufferSize;
        long cachedGatingSequence = this.cachedValue;

        /*
         * 生产者不追上消费者的逻辑：
         * 计算long wrapPoint = (cursorValue + requiredCapacity) - bufferSize;
         * 然后wrapPoint <= cachedGatingSequence就能保证生产者不追上消费者，而且为什么=也可以是因为INITIAL_CURSOR_VALUE = -1L，Sequence是从-1开始的
         */
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {

            // doStore就是是否要加入缓存屏障，在单线程的情况下nextValue始终是一致的，只有自己会修改，而调用setVolatile，是为了使用其方法中的内存屏障代码
            cursor.setVolatile(nextValue);  // StoreLoad fence

            long minSequence;
            // 等待消费者消费，后继续获取下一个存储槽位
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue)))
            {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }

            this.cachedValue = minSequence;
        }

        this.nextValue = nextSequence;

        return nextSequence;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(final int n) throws InsufficientCapacityException
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        // 这里hasAvailableCapacity 参数doStore就是在实际推进sequence
        if (!hasAvailableCapacity(n, true))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        long nextValue = this.nextValue;

        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#claim(long)
     * @see MultiProducerSequencer#claim(long)
     */
    @Override
    public void claim(final long sequence)
    {
        // 单生产者即单线程不需要加锁
        this.nextValue = sequence;
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(final long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * 单生产者直接推进到高位sequence，多生产者则需要一个一个推进
     *
     * @see Sequencer#publish(long, long)
     * @see MultiProducerSequencer#publish(long)
     */
    @Override
    public void publish(final long lo, final long hi)
    {
        publish(hi);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(final long sequence)
    {
        final long currentSequence = cursor.get();
        return sequence <= currentSequence && sequence > currentSequence - bufferSize;
    }

    @Override
    public long getHighestPublishedSequence(final long lowerBound, final long availableSequence)
    {
        return availableSequence;
    }

    @Override
    public String toString()
    {
        return "SingleProducerSequencer{" +
                "bufferSize=" + bufferSize +
                ", waitStrategy=" + waitStrategy +
                ", cursor=" + cursor +
                ", gatingSequences=" + Arrays.toString(gatingSequences) +
                '}';
    }

    private boolean sameThread()
    {
        return ProducerThreadAssertion.isSameThreadProducingTo(this);
    }

    /**
     * Only used when assertions are enabled.
     */
    private static class ProducerThreadAssertion
    {
        /**
         * Tracks the threads publishing to {@code SingleProducerSequencer}s to identify if more than one
         * thread accesses any {@code SingleProducerSequencer}.
         * I.e. it helps developers detect early if they use the wrong
         * {@link com.lmax.disruptor.dsl.ProducerType}.
         */
        private static final Map<SingleProducerSequencer, Thread> PRODUCERS = new HashMap<>();

        public static boolean isSameThreadProducingTo(final SingleProducerSequencer singleProducerSequencer)
        {
            synchronized (PRODUCERS)
            {
                final Thread currentThread = Thread.currentThread();
                if (!PRODUCERS.containsKey(singleProducerSequencer))
                {
                    PRODUCERS.put(singleProducerSequencer, currentThread);
                }
                return PRODUCERS.get(singleProducerSequencer).equals(currentThread);
            }
        }
    }
}
