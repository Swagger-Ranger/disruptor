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
 * Coordination barrier for tracking the cursor for publishers and sequence of
 * dependent {@link EventProcessor}s for processing a data structure
 * <p>
 * GatingSequence是协调消费者和生产者，而SequenceBarrier是协调多消费者消费
 */
public interface SequenceBarrier
{
    /**
     * Wait for the given sequence to be available for consumption.
     *
     * @param sequence to wait for
     * @return the sequence up to which is available
     * @throws AlertException       if a status change has occurred for the Disruptor
     * @throws InterruptedException if the thread needs awaking on a condition variable.
     * @throws TimeoutException     if a timeout occurs while waiting for the supplied sequence.
     */
    long waitFor(long sequence) throws AlertException, InterruptedException, TimeoutException;

    /**
     * Get the current cursor value that can be read.
     *
     * @return value of the cursor for entries that have been published.
     */
    long getCursor();

    /**
     * The current alert status for the barrier.
     *
     * @return true if in alert otherwise false.
     */
    boolean isAlerted();

    /**
     * Alert the {@link EventProcessor}s of a status change and stay in this status until cleared.
     * <p> alert 方法用于通知 SequenceBarrier 发生了异常或需要中断当前的事件处理。
     * 这通常是由事件处理器或其他组件触发的，目的是让 SequenceBarrier 知道有需要立即处理的问题。
     */
    void alert();

    /**
     * Clear the current alert status.
     */
    void clearAlert();

    /**
     * Check if an alert has been raised and throw an {@link AlertException} if it has.
     * <p> checkAlert 方法用于检查 SequenceBarrier 是否已经被标记为需要响应异常或中断。
     * 如果 alert 方法被调用设置了标志，checkAlert 方法将会检测到这一点并相应地处理。
     *
     * @throws AlertException if alert has been raised.
     */
    void checkAlert() throws AlertException;
}
