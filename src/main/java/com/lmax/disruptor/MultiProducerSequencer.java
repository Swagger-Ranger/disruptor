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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Suitable for use for sequencing across multiple publisher threads.
 *
 * <p>Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#next()}, to determine the highest available sequence that can be read, then
 * {@link Sequencer#getHighestPublishedSequence(long, long)} should be used.
 */
public final class MultiProducerSequencer extends AbstractSequencer
{
    //创建一个可以操作 int[] 数组元素的 VarHandle AVAILABLE_ARRAY 以高效安全地访问和修改下面 availableBuffer数组变量中的元素，而无需通过普通的同步手段
    private static final VarHandle AVAILABLE_ARRAY = MethodHandles.arrayElementVarHandle(int[].class);

    private final Sequence gatingSequenceCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    // availableBuffer tracks the state of each ringbuffer slot
    // see below for more details on the approach
    private final int[] availableBuffer;
    // 计算RingBuffer索引的掩码值。构造其中赋值 bufferSize -1，bufferSize为2的幂，则bufferSize -1就是有效位全是1
    private final int indexMask;
    //  构造其中赋值indexShift = Util.log2(bufferSize) 就是在确认bufferSize的2的对数，比如bufferSize=1024，则indexShift=10
    private final int indexShift;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public MultiProducerSequencer(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);

        // availableBuffer和 ringbuffer的bufferSize是一样的，用于追踪每个生产者在写入完成后，标记该槽位已经可供消费者读取
        availableBuffer = new int[bufferSize];
        Arrays.fill(availableBuffer, -1);

        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(gatingSequences, requiredCapacity, cursor.get());
    }

    private boolean hasAvailableCapacity(final Sequence[] gatingSequences, final int requiredCapacity, final long cursorValue)
    {
        long wrapPoint = (cursorValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > cursorValue)
        {
            long minSequence = Util.getMinimumSequence(gatingSequences, cursorValue);
            gatingSequenceCache.set(minSequence);

            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * final 修饰符是让参数不可改变，我理解这里参数加final修饰符，主要是为了确保sequence不会被修改，并且虚拟机和编译器对final修饰的变量也有更好的优化，
     * 还有就是传达了一个明确的编程意图：此参数不能被修改、以提示维护人员不要乱改sequence
     *
     * @see Sequencer#claim(long)
     * @see SingleProducerSequencer#claim(long)
     */
    @Override
    public void claim(final long sequence)
    {
        // cursor是个Sequence，其有内存屏障来维护一致性
        cursor.set(sequence);
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
        if (n < 1 || n > bufferSize)
        {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        // cursor.getAndAdd(n);是原子性的
        long current = cursor.getAndAdd(n);

        long nextSequence = current + n;
        long wrapPoint = nextSequence - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > current)
        {
            long gatingSequence;
            while (wrapPoint > (gatingSequence = Util.getMinimumSequence(gatingSequences, current)))
            {
                LockSupport.parkNanos(1L); // TODO, should we spin based on the wait strategy?
            }

            gatingSequenceCache.set(gatingSequence);
        }

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

        long current;
        long next;

        do
        {
            current = cursor.get();
            next = current + n;

            if (!hasAvailableCapacity(gatingSequences, n, current))
            {
                throw InsufficientCapacityException.INSTANCE;
            }
        }
        while (!cursor.compareAndSet(current, next));

        return next;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        long consumed = Util.getMinimumSequence(gatingSequences, cursor.get());
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(final long sequence)
    {
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * 单生产者直接推进到高位sequence
     * 多生产者模式需要逐个推进可以确保availableBuffer中的每个序号被正确标记为已发布，并且可以避免并发问题。
     * @see Sequencer#publish(long, long)
     * @see SingleProducerSequencer#publish(long, long)
     */
    @Override
    public void publish(final long lo, final long hi)
    {
        for (long l = lo; l <= hi; l++)
        {
            setAvailable(l);
        }
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * The below methods work on the availableBuffer flag.
     *
     * <p>The prime reason is to avoid a shared sequence object between publisher threads.
     * (Keeping single pointers tracking start and end would require coordination
     * between the threads).
     *
     * <p>--  Firstly we have the constraint that the delta between the cursor and minimum
     * gating sequence will never be larger than the buffer size (the code in
     * next/tryNext in the Sequence takes care of that).
     * -- Given that; take the sequence value and mask off the lower portion of the
     * sequence as the index into the buffer (indexMask). (aka modulo operator)
     * -- The upper portion of the sequence becomes the value to check for availability.
     * ie: it tells us how many times around the ring buffer we've been (aka division)
     * -- Because we can't wrap without the gating sequences moving forward (i.e. the
     * minimum gating sequence is effectively our last available position in the
     * buffer), when we have new data and successfully claimed a slot we can simply
     * write over the top.
     * <p>
     * 这个方法的注释就在处理，当快速消费，sequence又绕回相同的数组位置时，如何保证生产者不覆盖数据
     */
    private void setAvailable(final long sequence)
    {
        setAvailableBufferValue(calculateIndex(sequence), calculateAvailabilityFlag(sequence));
    }

    private void setAvailableBufferValue(final int index, final int flag)
    {
        AVAILABLE_ARRAY.setRelease(availableBuffer, index, flag);
    }

    /**
     * sequence是一个一直递增的数，而indexShift则是固定的就是bufferSize的2的幂，为什么比较两个就可以知道是否其可以使用：
     * <p> 假设 RingBuffer 大小为 1024，因此 indexShift = 10。
     * <p> 对于 sequence = 2047，其二进制表示是 00000000000000000000011111111111。
     *     经过 >>> 10 的右移后，得到 00000000000000000000000000000001，availability flag 是 1。
     * <p> 对于 sequence = 3071，其二进制表示是 00000000000000000000101111111111。
     *     经过 >>> 10 的右移后，得到 00000000000000000000000000000010，availability flag 是 2。
     * <p> 所以获得对应 sequence位置中 availableBuffer的使用标记会一直改变，只需要重新计算sequence的标记然后对比已有的数据中的标记就知道是否可以用
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(final long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        return (int) AVAILABLE_ARRAY.getAcquire(availableBuffer, index) == flag;
    }

    @Override
    public long getHighestPublishedSequence(final long lowerBound, final long availableSequence)
    {
        for (long sequence = lowerBound; sequence <= availableSequence; sequence++)
        {
            if (!isAvailable(sequence))
            {
                return sequence - 1;
            }
        }

        return availableSequence;
    }

    /**
     * 返回sequence除以 bufferSize的值并减去取bufferSize模余数的值，再将值按int截断，这个值会不断的增加，甚至会超过bufferSize
     * 但这不影响其处理 availableBuffer数组标记对应 RingBuffer 标记位置的可用性，因为isAvailable(final long sequence)中只需要比较前后两者而不是直接用其值
     */
    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift);
    }

    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }

    @Override
    public String toString()
    {
        return "MultiProducerSequencer{" +
                "bufferSize=" + bufferSize +
                ", waitStrategy=" + waitStrategy +
                ", cursor=" + cursor +
                ", gatingSequences=" + Arrays.toString(gatingSequences) +
                '}';
    }
}
