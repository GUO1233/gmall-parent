package com.atguigu.gmall.order.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${ware.url}")
    private String WARE_URl;

    @Autowired
    private RabbitService rabbitService;

    @Transactional
    @Override
    public Long saveOrderInfo(OrderInfo orderInfo) {
         /*
            保存应该有两张表：
            orderInfo ,orderDetail;
            传递传递的数据保存，如果表中有部分字段，前台并没有传递，则需要我们手动填写。
         */
        //  总金额，订单状态，【userId】,第三方交易编号，订单的主体描述，创建时间，过期时间，进度状态
        //  前提是OrderInfo 中必须有订单明细。


        orderInfo.sumTotalAmount();
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        //按照某个规则进行随机生产，保证不能重复。
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);

        //放入字符串，真正环境{可以给商品的名称}
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuilder sb = new StringBuilder();
//        for (OrderDetail orderDetail : orderDetailList) {
//             sb.append(orderDetail.getSkuName());
//        }
//        //  限制：长度 不能超过两百。
//        if (sb.length()>200){
//            //  拼接
//            orderInfo.setTradeBody(sb.substring(0,200));
//        }else {
//            orderInfo.setTradeBody(sb.toString());
//        }

        orderInfo.setTradeBody("冬天买大衣");
        orderInfo.setCreateTime(new Date());
        //过期时间为一天
        Calendar calendar = Calendar.getInstance();
        // 表示在当前系统时间+1天
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());

        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        // 订单主表保存   my:先保存 id自动注入 然后才能orderInfo.getId()
        orderInfoMapper.insert(orderInfo);
        // 保存订单明细
        List<OrderDetail> orderDetailLists = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailLists) {
            orderDetail.setOrderId(orderInfo.getId());
            //保存
            orderDetailMapper.insert(orderDetail);

            //my:保存完订单应该把购物车中的数据删除掉，但是我们为了方便测试下订单就没有删除
        }


        //发送延迟队列，如果定时未支付，取消订单
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,MqConst.ROUTING_ORDER_CANCEL,orderInfo.getId(),MqConst.DELAY_TIME);

        //  返回订单Id
        return orderInfo.getId();
    }

    @Override
    public String getTradNo(String userId) {
        // 定义key
        String tradeNoKey = "user:"+userId+":tradeCode";
        //定义一个流水号
        String tradeNo = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(tradeNoKey,tradeNo);
        return tradeNo;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        //定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String redisTradeNo  = (String) redisTemplate.opsForValue().get(tradeNoKey);
        //my:前端传过来的流水号
        return tradeCodeNo.equals(redisTradeNo);
    }

    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey  = "user:" + userId + ":tradeCode";
        // 删除数据
        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        //  查询库存：远程调用ware-manage
        //  在这不能使用feign！ware-manage 单独spring boot 项目！
        //  远程工具类调用
        //  WARE_URL = http://localhost:9001
        String flag= HttpClientUtil.doGet(WARE_URl+"/hasStock?skuId=" + skuId + "&num=" + skuNum);
        //  flag = 0 表示没有足够的库存。
        //  如果是1 表示有足够的库存，否则没有！
        return "1".equals(flag);
    }

    @Override
    public void execExpiredOrder(Long orderId) {
        updateOrderStatus(orderId, ProcessStatus.CLOSED);
    }

    private void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }
//--------------------------支付----------------------------
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id",orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(queryWrapper);
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }


}
