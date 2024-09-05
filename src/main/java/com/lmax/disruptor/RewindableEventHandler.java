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

/**
 * Callback interface to be implemented for processing events as they become available in the {@link RingBuffer}
 * with support for throwing a {@link RewindableException} when an even cannot be processed currently but may succeed on retry.
 * <p>
 * 处理那些抛出RewindableException异常可以回溯的事件，防止因为某些错误导致整个系统崩溃或数据丢失。
 * 在BatchEventProcessor里会根据构造器中，EventHandlerBase<? super T> eventHandler传入的 eventHandler来给处理逻辑加入回滚处理的不同RewindHandler实现类
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 * @see BatchEventProcessor#setExceptionHandler(ExceptionHandler) if you want to handle exceptions propagated out of the handler.
 */
public interface RewindableEventHandler<T> extends EventHandlerBase<T>
{
    /**
     * Called when a publisher has published an event to the {@link RingBuffer}.  The {@link BatchEventProcessor} will
     * read messages from the {@link RingBuffer} in batches, where a batch is all of the events available to be
     * processed without having to wait for any new event to arrive.  This can be useful for event handlers that need
     * to do slower operations like I/O as they can group together the data from multiple events into a single
     * operation.  Implementations should ensure that the operation is always performed when endOfBatch is true as
     * the time between that message and the next one is indeterminate.
     * <p>
     *  当发布者将事件发布到RingBuffer时调用。BatchEventProcessor将从RingBuffer中批量读取消息，其中批量是所有可以处理的事件，而不必等待任何新事件到达。
     *  对于需要执行I/ O等较慢操作的事件处理程序来说，这很有用，因为它们可以将多个事件中的数据组合为一个操作。实现应该确保当endOfBatch为true时总是执行操作，
     *  因为该消息与下一个消息之间的时间是不确定的。
     *
     * @param event      published to the {@link RingBuffer}
     * @param sequence   of the event being processed
     * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
     * @throws RewindableException if the EventHandler would like the batch event processor to process the entire batch again.
     * @throws Exception if the EventHandler would like the exception handled further up the chain.
     */
    @Override
    void onEvent(T event, long sequence, boolean endOfBatch) throws RewindableException, Exception;
}
