package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OrderService extends IService<OrderInfo> {
    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);
    /**
     * 生产流水号
     * @param userId
     * @return
     */
    String getTradNo(String userId);

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    boolean checkTradeCode(String userId,String tradeCodeNo);

    /**
     * 删除流水号
     * @param userId
     */
    void deleteTradeNo(String userId);

    /**
     * 验证库存
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean checkStock(Long skuId,Integer skuNum);

    void execExpiredOrder(Long orderId);

    //==================支付调用=====================
    /**
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);

}
