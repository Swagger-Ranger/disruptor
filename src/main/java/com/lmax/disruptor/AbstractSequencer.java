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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Base class for the various sequencer types (single/multi).  Provides
 * common functionality like the management of gating sequences (add/remove) and
 * ownership of the current cursor.
 * <P>Sequencer 的核心实现类通常是继承自 AbstractSequencer，其提供了 Sequencer 的基本实现和一些共享的功能。</P>
 */
public abstract class AbstractSequencer implements Sequencer
{
    private static final AtomicReferenceFieldUpdater<AbstractSequencer, Sequence[]> SEQUENCE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(AbstractSequencer.class, Sequence[].class, "gatingSequences");

    protected final int bufferSize;
    protected final WaitStrategy waitStrategy;
    /**
     * cursor是AbstractSequencer的核心变量，在整个Disruptor框架中，cursor也是一个全局变量，用于跟踪 RingBuffer 中的当前写入位置（序列号）即RingBuffer中的写入指针，
     * 表示RingBuffer的当前状态：即哪些数据是可用的，哪些数据已经发布并可供消费者读取。
     * <p>
     * 生产者和消费者通过 cursor 来间接进行同步和通信，生产者更新 cursor，消费者读取 cursor 来决定是否可以继续读取数据。
     * 因此，不需要区分生产者和消费者的cursor，这简化了同步逻辑，减少锁的竞争。生产者和消费者通过 Sequence 类和 cursor 进行无锁的同步，这也是 Disruptor 高效的原因之一。
     * <p>
     * 具体的生产者和消费者操作：当生产者线程写入新的事件数据并调用publish方法时，cursor会被更新到最新的序列号，这意味着，cursor 始终表示最近发布的事件的位置（或序列号）。
     * 而消费者通过 SequenceBarrier 或 Sequencer.getHighestPublishedSequence() 方法获取 cursor 的值来确定可以消费的数据的范围。
     */
    protected final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    protected volatile Sequence[] gatingSequences = new Sequence[0];

    /**
     * Create with the specified buffer size and wait strategy.
     *
     * @param bufferSize   The total number of entries, must be a positive power of 2.
     * @param waitStrategy The wait strategy used by this sequencer
     */
    public AbstractSequencer(final int bufferSize, final WaitStrategy waitStrategy)
    {
        if (bufferSize < 1)
        {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        this.bufferSize = bufferSize;
        this.waitStrategy = waitStrategy;
    }

    /**
     * @see Sequencer#getCursor()
     */
    @Override
    public final long getCursor()
    {
        return cursor.get();
    }

    /**
     * @see Sequencer#getBufferSize()
     */
    @Override
    public final int getBufferSize()
    {
        return bufferSize;
    }

    /**
     * @see Sequencer#addGatingSequences(Sequence...)
     */
    @Override
    public final void addGatingSequences(final Sequence... gatingSequences)
    {
        SequenceGroups.addSequences(this, SEQUENCE_UPDATER, this, gatingSequences);
    }

    /**
     * @see Sequencer#removeGatingSequence(Sequence)
     */
    @Override
    public boolean removeGatingSequence(final Sequence sequence)
    {
        return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
    }

    /**
     * @see Sequencer#getMinimumSequence()
     */
    @Override
    public long getMinimumSequence()
    {
        return Util.getMinimumSequence(gatingSequences, cursor.get());
    }

    /**
     * @see Sequencer#newBarrier(Sequence...)
     */
    @Override
    public SequenceBarrier newBarrier(final Sequence... sequencesToTrack)
    {
        // SequenceBarrier实现都是在 ProcessingSequenceBarrier 中将多个sequencesToTrack转化成FixedSequenceGroup来简化处理的，因为其实只需要关注最小的一个Sequence即可
        return new ProcessingSequenceBarrier(this, waitStrategy, cursor, sequencesToTrack);
    }

    /**
     * Creates an event poller for this sequence that will use the supplied data provider and
     * gating sequences.
     *
     * @param dataProvider    The data source for users of this event poller
     * @param gatingSequences Sequence to be gated on.
     * @return A poller that will gate on this ring buffer and the supplied sequences.
     */
    @Override
    public <T> EventPoller<T> newPoller(final DataProvider<T> dataProvider, final Sequence... gatingSequences)
    {
        return EventPoller.newInstance(dataProvider, this, new Sequence(), cursor, gatingSequences);
    }

    @Override
    public String toString()
    {
        return "AbstractSequencer{" +
            "waitStrategy=" + waitStrategy +
            ", cursor=" + cursor +
            ", gatingSequences=" + Arrays.toString(gatingSequences) +
            '}';
    }
}