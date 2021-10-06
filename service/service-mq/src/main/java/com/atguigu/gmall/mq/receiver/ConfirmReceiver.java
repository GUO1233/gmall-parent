package com.atguigu.gmall.mq.receiver;

import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 消息接收端
 */
@Component
@Configuration
public class ConfirmReceiver {

    //  根据交换机，路由键来监听消息
    //  @Queue() 表示队列 queue.confirm     autoDelete = "false",durable = "true"
    //  @Exchange() 表示交换机 exchange.confirm
    //  @Key {} 表示路由键 routing.confirm
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "queue.confirm",autoDelete = "false"),//queue.confirm 队列名字
            exchange = @Exchange(value = "exchange.confirm",autoDelete = "true"),
            key = {"routing.confirm"} //关键字
    ))
    public void process(Message message, Channel channel){
        /**
         * //  System.out.println("message:\t"+message.getBody().toString());
         *         //  如何获取到消息
         *         //  手动确认消息 ack， 如果消费失败，那么是nack
         *         //  确认一个消息 然后将这个消息移除。
         *         //  如果是true , 表示循环将队列中的消息删除！
         */
        System.out.println("RabbitListener:"+new String(message.getBody()));
        // 采用手动应答模式, 手动确认应答更为安全稳定
        //如果手动确定了，再出异常，mq不会通知；如果没有手动确认，抛异常mq会一直通知
//        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        try {
            int i=1/0;
            //false 确认一个消息，true批量确认
            System.out.println("message:\t"+new String(message.getBody()));
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){
            //消息是否再次被拒绝！
            System.out.println("come on!");
            // getRedelivered() 判断是否已经处理过一次消息
            if (message.getMessageProperties().getRedelivered()){
                System.out.println("消息已重复处理,拒绝再次接收");
                // 拒绝消息，requeue=false 表示不再重新入队，如果配置了死信队列则进入死信队列
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);//my:消息被第一次拒绝接收后状态从Ready变为Unack，然后又会重新投递一次被拒绝后这个消息就被消费了

            }else{
                System.out.println("消息即将再次返回队列处理");
                // 参数二：是否批量， 参数三：为是否重新回到队列，true重新入队
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true);//my:消息从Unacked的状态变为Ready状态
            }
        }
    }


    //=========================================一下是老师的代码,方法上多了一个参数=============================================

   /* //  根据交换机，路由键来监听消息
    //  @Queue() 表示队列 queue.confirm     autoDelete = "false",durable = "true"
    //  @Exchange() 表示交换机 exchange.confirm
    //  @Key {} 表示路由键 routing.confirm
    @SneakyThrows
    @RabbitListener(bindings=@QueueBinding(
            value = @Queue(value = "queue.confirm",autoDelete = "false",durable = "true"),
            exchange = @Exchange(value = "exchange.confirm"),
            key = {"routing.confirm"}
    ))
    public void getMsg(String msg, Message message, Channel channel){

        //  System.out.println("message:\t"+message.getBody().toString());
        //  如何获取到消息
        //  手动确认消息 ack， 如果消费失败，那么是nack
        //  确认一个消息 然后将这个消息移除。
        //  如果是true , 表示循环将队列中的消息删除！
        try {
            //  获取消息！
            System.out.println("msg:\t"+msg);
            System.out.println("message:\t"+new String(message.getBody()));
            //  如果处理有异常则会走catch
            //  int i = 1/0;
            //  手动确认！
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            //  异常要写Exception
        } catch (Exception e) {

            //  getRedelivered() = true; 则表示消息已经处理过一次了！
            if (message.getMessageProperties().getRedelivered()){
                System.out.println("消息已重复处理,拒绝再次接收");

                // basicReject();
                //  拒绝消息，requeue=false 表示不再重新入队，如果配置了死信队列则进入死信队列
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            }else {
                System.out.println("消息即将再次返回队列处理");
                // 参数二：是否批量， 参数三：为是否重新回到队列，true重新入队
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }*/

}
