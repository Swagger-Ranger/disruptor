/*
 * Copyright 2012 LMAX Ltd.
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
 * Coordinates claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
public interface Sequencer extends Cursored, Sequenced
{
    /**
     * Set to -1 as sequence starting point
     */
    long INITIAL_CURSOR_VALUE = -1L;

    /**
     * Claim a specific sequence.  Only used if initialising the ring buffer to
     * a specific value.
     * <p>claim(long sequence)的作用是将 RingBuffer 内部的游标（cursor）移动到指定的 sequence 位置。
     * <p> claim 方法在 Disruptor 框架中通常不需要用户直接调用
     * 这意味着：游标更新，游标会被直接设置到这个新的 sequence 值。
     * <p>如果新游标位置在当前游标位置之后（即向前推进游标）：会覆盖掉 RingBuffer 从当前游标位置到新的游标位置之间的旧数据，任何尚未被消费者处理的数据都将被视为丢弃。
     * <p>如果新游标位置在当前游标位置之前（即后前重置游标）：消费者会再次消费这个位置以及之后的数据。因此，数据可能会被重复消费，
     *                                               在使用中很少直接手动将游标设置到一个更早的位置，除非明确知道这样做的后果，并且有严格的应用逻辑来处理数据的一致性。
     * @param sequence The sequence to initialise too.
     */
    void claim(long sequence);

    /**
     * Confirms if a sequence is published and the event is available for use; non-blocking.
     *
     * @param sequence of the buffer to check
     * @return true if the sequence is available for use, false if not
     */
    boolean isAvailable(long sequence);

    /**
     * Add the specified gating sequences to this instance of the Disruptor.  They will
     * safely and atomically added to the list of gating sequences.
     * <p>
     * 向 Sequencer 实例添加一个或多个gating sequences（也称为“门控序列”），用来控制生产者的进度确保消费者能够跟上生产者的速度，防止生产者“跑得太快”，覆盖尚未处理完的数据。
     * <p>
     * 加入的Sequence就是对应消费者的消费序列，即gating sequences 中保存的是所有消费者的序列位置。
     * 当生产者要写入新数据时，它会检查 gating sequences 的最小值，生产者需要确保它即将写入的位置不会超过任何一个 gating sequence。
     * 如果生产者发现所有的消费者（gating sequences）都已经处理完当前的生产者所处位置，那么生产者就可以继续写入数据，
     * 如果有任何一个消费者的 Sequence 小于生产者的写入位置，生产者会阻塞，等待消费者处理完毕，Disruptor 会阻塞生产者线程直到可以安全写入。
     * <p>
     * GatingSequence其实就是处理多消费者的手段，因为有多消费者所以才需要把每个需要确保消费完成的消费者序列加入监控，以避免生产者覆盖数据。
     * <p>
     * 如果有消费者可以不加入其Gating Sequence ，就是不保证其会消费所有消息，属于是消费到哪些就消费哪些，生产者不会考虑其消费情况一直填入数据即可
     *
     * @param gatingSequences The sequences to add.
     */
    void addGatingSequences(Sequence... gatingSequences);

    /**
     * Remove the specified sequence from this sequencer.
     *
     * @param sequence to be removed.
     * @return <code>true</code> if this sequence was found, <code>false</code> otherwise.
     */
    boolean removeGatingSequence(Sequence sequence);

    /**
     * Create a new SequenceBarrier to be used by an EventProcessor to track which messages
     * are available to be read from the ring buffer given a list of sequences to track.
     * GatingSequence是协调消费者和生产者，而SequenceBarrier是协调多消费者消费
     *
     * @param sequencesToTrack All of the sequences that the newly constructed barrier will wait on.
     * @return A sequence barrier that will track the specified sequences.
     * @see SequenceBarrier
     */
    SequenceBarrier newBarrier(Sequence... sequencesToTrack);

    /**
     * Get the minimum sequence value from all of the gating sequences
     * added to this ringBuffer.
     *
     * @return The minimum gating sequence or the cursor sequence if
     * no sequences have been added.
     * <P>生产者能安全的生产数据的位置
     */
    long getMinimumSequence();

    /**
     * Get the highest sequence number that can be safely read from the ring buffer.  Depending
     * on the implementation of the Sequencer this call may need to scan a number of values
     * in the Sequencer.  The scan will range from nextSequence to availableSequence.  If
     * there are no available values <code>&gt;= nextSequence</code> the return value will be
     * <code>nextSequence - 1</code>.  To work correctly a consumer should pass a value that
     * is 1 higher than the last sequence that was successfully processed.
     * <P>消费者能安全读取到的位置
     *
     * @param nextSequence      The sequence to start scanning from.
     * @param availableSequence The sequence to scan to.
     * @return The highest value that can be safely read, will be at least <code>nextSequence - 1</code>.
     */
    long getHighestPublishedSequence(long nextSequence, long availableSequence);

    /**
     * Creates an event poller from this sequencer
     * 提供基于轮询的方式来处理事件
     *
     * @param provider from which events are drawn
     * @param gatingSequences sequences to be gated on
     * @param <T> the type of the event
     * @return the event poller
     */
    <T> EventPoller<T> newPoller(DataProvider<T> provider, Sequence... gatingSequences);
}