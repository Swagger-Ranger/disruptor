package com.lmax.disruptor;

/**
 * Operations related to the sequencing of items in a {@link RingBuffer}.
 * See the two child interfaces, {@link Sequencer} and {@link EventSequencer} for more details.
 */
public interface Sequenced
{
    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    int getBufferSize();

    /**
     * Has the buffer got capacity to allocate another sequence.  This is a concurrent
     * method so the response should only be taken as an indication of available capacity.
     *
     * @param requiredCapacity in the buffer
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    boolean hasAvailableCapacity(int requiredCapacity);

    /**
     * Get the remaining capacity for this sequencer.
     *
     * @return The number of slots remaining.
     */
    long remainingCapacity();

    /**
     * Claim the next event in sequence for publishing.
     * 如果没有可用的，会阻塞
     *
     * @return the claimed sequence value
     */
    long next();

    /**
     * Claim the next n events in sequence for publishing.  This is for batch event producing.  Using batch producing
     * requires a little care and some math.
     * <p> 注意：返回的是sequence value，用法需要按下面的例子来使用
     * <p> 和{@link Sequencer#claim(long) }的区别是：
     * claim：直接手动设置某个序号的值将某个序号标记为已占用，不经过复杂的可用性检查，一般不会由用户直接调用，而是由 Disruptor 框架内部在特定的场景下调用
     * next：才是用户调用的方法，为生产者分配下一个可用的序号，生产者调用该方法来获取 RingBuffer 中可用的槽位。
     * <pre>
     * int n = 10;
     * long hi = sequencer.next(n);
     * long lo = hi - (n - 1);
     * for (long sequence = lo; sequence &lt;= hi; sequence++) {
     *     // Do work.
     * }
     * sequencer.publish(lo, hi);
     * </pre>
     *
     * @param n the number of sequences to claim
     * @return the highest claimed sequence value
     */
    long next(int n);

    /**
     * Attempt to claim the next event in sequence for publishing.  Will return the
     * number of the slot if there is at least <code>requiredCapacity</code> slots
     * available.
     * 如果没有可用的，会立刻抛异常不会阻塞，而且InsufficientCapacityException是一个受检异常
     *
     * @return the claimed sequence value
     * @throws InsufficientCapacityException thrown if there is no space available in the ring buffer.
     */
    long tryNext() throws InsufficientCapacityException;

    /**
     * Attempt to claim the next n events in sequence for publishing.  Will return the
     * highest numbered slot if there is at least <code>requiredCapacity</code> slots
     * available.  Have a look at {@link Sequencer#next()} for a description on how to
     * use this method.
     *
     * @param n the number of sequences to claim
     * @return the claimed sequence value
     * @throws InsufficientCapacityException thrown if there is no space available in the ring buffer.
     */
    long tryNext(int n) throws InsufficientCapacityException;

    /**
     * Publishes a sequence. Call when the event has been filled.
     *
     * @param sequence the sequence to be published.
     */
    void publish(long sequence);

    /**
     * Batch publish sequences.  Called when all of the events have been filled.
     * 批量推进sequences，并且lo是最小的sequence，RingBuffer中的sequence必须是连续的，即两个sequence都是已发布的状态则中间的所有sequence都是已发布的状态
     *
     * @param lo first sequence number to publish
     * @param hi last sequence number to publish
     */
    void publish(long lo, long hi);
}