package com.alibaba.dubbo.tracer.client.support.rocketmq;

import java.util.List;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.tracer.api.Span;
import com.alibaba.dubbo.tracer.api.TracingCollector;
import com.alibaba.dubbo.tracer.client.DstConstants;
import com.alibaba.fastjson.JSON;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.client.producer.SendStatus;
import com.alibaba.rocketmq.common.message.Message;

public class RocketMqTracingCollector implements TracingCollector {
	
	private Logger logger = LoggerFactory.getLogger(RocketMqTracingCollector.class);

    private DefaultMQProducer defaultMQProducer;

    public RocketMqTracingCollector() {
        defaultMQProducer = new DefaultMQProducer(DstConstants.ROCKET_MQ_PRODUCER);
        defaultMQProducer.setNamesrvAddr(ConfigUtils.getProperty(DstConstants.ROCKET_MQ_NAME_SRV_ADD));
        try {
            defaultMQProducer.start();
        } catch (MQClientException e) {
            throw new IllegalArgumentException("fail to start rocketmq producer.",e);
        }
    }

    @Override
    public void push(List<Span> spanList) {
        byte[] bytes = JSON.toJSONBytes(spanList);
        Message message = new Message(DstConstants.ROCKET_MQ_TOPIC,bytes);
        try {
            SendResult sendResult = defaultMQProducer.send(message);
            if(sendResult.getSendStatus()!= SendStatus.SEND_OK){
                logger.error("send mq message return ["+sendResult.getSendStatus()+"]");
            }
        } catch (Exception e) {
            logger.error("fail to send message.",e);
        }
    }
}
