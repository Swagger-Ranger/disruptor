package com.lmax.disruptor.simpleDemo;

import com.lmax.disruptor.EventFactory;

/**
 * @author liufei
 **/
public class LongEventFactory implements EventFactory<LongEvent> {
    @Override
    public LongEvent newInstance() {
        return new LongEvent();
    }
}
