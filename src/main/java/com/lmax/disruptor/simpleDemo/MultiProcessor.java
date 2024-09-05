package com.lmax.disruptor.simpleDemo;

import com.lmax.disruptor.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 多消费者
 * @author liufei
 **/
public class MultiProcessor {

    public static void main(String[] args) throws InterruptedException {
        // 创建一个RingBuffer，容量为1024
        RingBuffer<Event> ringBuffer = RingBuffer.createSingleProducer(Event::new, 1024, new BlockingWaitStrategy());

        // 创建两个SequenceBarrier，用于控制EventProcessor之间的同步
        SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        // 创建BatchEventProcessorBuilder来创建EventProcessor
        BatchEventProcessor<Event> eventProcessor1 = new BatchEventProcessorBuilder().build(ringBuffer, sequenceBarrier, new EventHandler1());
        BatchEventProcessor<Event> eventProcessor2 = new BatchEventProcessorBuilder().build(ringBuffer, sequenceBarrier, new EventHandler2());

        // 把两个事件处理器的Sequence添加到RingBuffer中
        ringBuffer.addGatingSequences(eventProcessor1.getSequence(), eventProcessor2.getSequence());

        // 启动事件处理器，使用线程池
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(eventProcessor1);
        executor.submit(eventProcessor2);

        // 发布事件到RingBuffer
        for (long i = 0; i < 10; i++) {
            long sequence = ringBuffer.next(); // 获得下一个可用的序号
            try {
                Event event = ringBuffer.get(sequence); // 获取事件对象
                event.setValue(i); // 填充事件数据
            } finally {
                ringBuffer.publish(sequence); // 发布事件
            }
        }

        // sleep一下，不然消费者还没有开始消费就被halt了
        TimeUnit.SECONDS.sleep(1);
        // 停止事件处理器
        eventProcessor1.halt();
        eventProcessor2.halt();
        executor.shutdown();
    }


    static class EventHandler1 implements EventHandler<Event> {
        @Override
        public void onEvent(Event event, long sequence, boolean endOfBatch) {
            System.out.println("EventHandler1: " + event.getValue());
        }
    }

    static class EventHandler2 implements EventHandler<Event> {
        @Override
        public void onEvent(Event event, long sequence, boolean endOfBatch) {
            System.out.println("EventHandler2: " + event.getValue());
        }
    }

    static class Event {
        private long value;

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }
    }
}
