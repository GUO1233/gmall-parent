package com.atguigu.gmall.mq.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/mq")
@Slf4j
public class MqController {

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RabbitTemplate rabbitTemplate;


    /**
     * 消息发送
     * @return
     */
    //http://cart.gmall.com/8282/mq/sendConfirm
    @GetMapping("sendConfirm")
    public Result sendConfirm() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitService.sendMessage("exchange.confirm","routing.confirm",sdf.format(new Date()));

        return Result.ok();
    }

    //============死信队列====================

    /**
     * my:私信队列发送消息到exchange_dead路由，路由关键字routing_dead_1绑定的queue_dead_1队列投递到这个队列把消息，但是由于没有接收者，消息TTl过期，变为私信，
     * 由私信路由发送到routing_dead_2队列，由监听queue_dead_2队列的方法执行
     * @return
     */
    @GetMapping("sendDeadLettle")
    public Result sendDeadLettle(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.rabbitTemplate.convertAndSend(DeadLetterMqConfig.exchange_dead,DeadLetterMqConfig.routing_dead_1,"ok");
        System.out.println(sdf.format(new Date()) + " Delay sent.");
        return Result.ok();
    }

    //================死信插件==============
    /**
     *my:路由直接配置为私信路由，消息setDelay(10*1000);秒后直接发送到死信路由然后转发到私信队列，监听私信队列的方法就会执行
     * @return
     */
    @GetMapping("sendDelay")
    public Result sendDelay() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.rabbitTemplate.convertAndSend(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay, sdf.format(new Date()), new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setDelay(10*1000);
                System.out.println(sdf.format(new Date())+" Delay sent.");
                return message;
            }
        });
        return Result.ok();
    }

}
