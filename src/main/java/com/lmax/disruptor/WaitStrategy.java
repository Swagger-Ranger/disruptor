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
 * Strategy employed for making {@link EventProcessor}s wait on a cursor {@link Sequence}.
 * 定义Disruptor 框架中的等待策略，两个方法类似于Java中的wait和notifyAll方法
 * @see com.lmax.disruptor.BlockingWaitStrategy 使用synchronized阻塞的方式来等待数据的到来。
 * @see com.lmax.disruptor.LiteBlockingWaitStrategy 我理解整体逻辑和BlockingWaitStrategy一致，但使用了一个AtomicBoolean signalNeeded来避免notifyAll的惊群效应。
 * @see com.lmax.disruptor.SleepingWaitStrategy 在等待时使用线程休眠的方式来减少 CPU 的消耗。这种策略是性能和CPU资源之间的一个很好的折衷。
 * @see com.lmax.disruptor.YieldingWaitStrategy 通过先自旋，自旋SPIN_TRIES次后自旋然后yield。响应极快但如果繁忙时，等待更久的线程反倒会放弃CPU时间。
 * @see com.lmax.disruptor.BusySpinWaitStrategy 在等待期间全程Thread.onSpinWait()自旋，适用于低延迟且处理能力强的场景。
 * @see com.lmax.disruptor.TimeoutBlockingWaitStrategy  当阻塞时通过周期性的抛出TimeoutException来唤醒并继续执行，当吞吐量和低延迟不如CPU资源重要时使用这种策略。
 * @see com.lmax.disruptor.LiteTimeoutBlockingWaitStrategy TimeoutBlockingWaitStrategy的变体，也是通过使用了一个AtomicBoolean signalNeeded来没有争用地唤醒
 * @see com.lmax.disruptor.PhasedBackoffWaitStrategy 分阶段等待策略，当吞吐量和低延迟不如CPU资源重要时，可以使用这种策略。先Spins，然后yield，最后再使用配置的等待策略
 */
public interface WaitStrategy
{
    /**
     * Wait for the given sequence to be available.  It is possible for this method to return a value
     * less than the sequence number supplied depending on the implementation of the WaitStrategy.  A common
     * use for this is to signal a timeout.  Any EventProcessor that is using a WaitStrategy to get notifications
     * about message becoming available should remember to handle this case.  The {@link BatchEventProcessor} explicitly
     * handles this case and will signal a timeout if required.
     *
     * @param sequence          to be waited on.要等待的目标序列号，消费者会阻塞等待，直到这个序列号的数据可用
     * @param cursor            the main sequence from ringbuffer. Wait/notify strategies will
     *                          need this as it's the only sequence that is also notified upon update.，cursor 指示 Ring Buffer 中当前已被写入的最新数据序列号。
     * @param dependentSequence on which to wait.这个一般就是多个Sequence的 FixedSequenceGroup
     * @param barrier           the processor is waiting on. SequenceBarrier
     * @return the sequence that is available which may be greater than the requested sequence.可以安全读取的最高序列号。它可能是比请求的序列号更大的值。
     * @throws AlertException       if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     * @throws TimeoutException if a timeout occurs before waiting completes (not used by some strategies)
     */
    long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException;

    /**
     * Implementations should signal the waiting {@link EventProcessor}s that the cursor has advanced.
     * 当等待策略在阻塞状态时（即线程被阻塞），此方法会通知所有等待的 EventProcessor，使它们知道有新的数据可以处理。
     */
    void signalAllWhenBlocking();
}
