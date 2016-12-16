package com.alibaba.dubbo.tracer.client.support.rocketmq;

import com.alibaba.dubbo.tracer.api.TracingCollector;
import com.alibaba.dubbo.tracer.client.TracingCollectorFactory;

public class RocketMqTracingCollectorFactory implements TracingCollectorFactory {

    @Override
    public TracingCollector getTracingCollector() {
        return new RocketMqTracingCollector();
    }

}
