package com.atguigu.gmall.order.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderReceiver {
    @Autowired
    private OrderService orderService;

    /**
     * 取消订单消费者
     * 延迟队列，不能再这里做交换机与队列绑定
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        if (null != orderId) {
            //防止重复消费
            OrderInfo orderInfo  = orderService.getById(orderId);
            if (null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())) {
                //取消订单
                orderService.execExpiredOrder(orderId);
            }
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
