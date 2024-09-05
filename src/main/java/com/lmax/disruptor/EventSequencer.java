package com.lmax.disruptor;

/**
 * Pulls together the low-level data access and sequencing operations of {@link RingBuffer}
 * <p> 一个聚合接口，将底层的数据访问 DataProvider和 序列号操作 Sequenced聚合在一起
 * @param <T> The event type
 */
public interface EventSequencer<T> extends DataProvider<T>, Sequenced
{

}
