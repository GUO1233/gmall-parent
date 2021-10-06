package com.atguigu.gmall.order.controller;

import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;



    /**
     * 确认订单
     * @param request
     * @return
     */
    @GetMapping("auth/trade")//my:这个方法是先把数据渲染到前端页面 trad.html页面 也就是准备提交订单的页面
    public Result<Map<String, Object>> trade(HttpServletRequest request) {
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);

        //获取用户地址
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        // 渲染送货清单
        // 先得到用户想要购买的商品！
        List<CartInfo> cartInfoList = cartFeignClient.getCartCheckedList(userId);
        // 声明一个集合来存储订单明细
        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();
        for (CartInfo cartInfo : cartInfoList) {
            OrderDetail orderDetail = new OrderDetail();

            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());

            // 添加到集合
            detailArrayList.add(orderDetail);
        }
        // 计算总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        orderInfo.sumTotalAmount();

        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", detailArrayList);
        // 保存总金额
        result.put("totalNum", detailArrayList.size());
        result.put("totalAmount", orderInfo.getTotalAmount());


        //设置流水号
        String tradNo = orderService.getTradNo(userId);
        result.put("tradeNo",tradNo);
        return Result.ok(result);

    }

    /**
     * 提交订单
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("auth/submitOrder") //my:前端页面发起提交请求           这个是点击提交按钮把保存订单数据,防止重复提交表单，异步编排验证库存，验证价格
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request){

        //获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 【userId】
        orderInfo.setUserId(Long.parseLong(userId));

        //  在保存之前执行 http://api.gmall.com/api/order/auth/submitOrder?tradeNo=null     - Path=/*/order/**   - id: service-order
        //  获取到前台传递过来的流水号
        String tradeNo = request.getParameter("tradeNo");
        //调用比较方法
        //表示流水号不一致
        if(!orderService.checkTradeCode(userId,tradeNo)){//public boolean checkTradeCode(String userId, String tradeCodeNo)/
            //  提示消息
            return Result.fail().message("不能重复提交订单！");
        }
        //  删除
        orderService.deleteTradeNo(userId);
//================================================================================
        // 验证库存：需要每个商品进行验证
      /*  List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            //远程调用验证
            boolean res = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
            // res=true 表示有库存，false 表示库存不足
            if (!res){
                // 提示消息
                return Result.fail().message(orderDetail.getSkuName()+"库存不足！");
            }
            //验证价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
            //orderDetail.getOrderPrice()是从缓存中获取数据，skuPrice是从数据库获取的实时价格
            if (orderDetail.getOrderPrice().compareTo(skuPrice) !=0){
                //重新查询价格
                cartFeignClient.loadCartCache(userId);
                return Result.fail().message(orderDetail.getSkuName()+"价格有变动！");
            }
        }*/

        //声明一个异步编排的集合，还需要一个获取每个CompletableFuture结果集的集合！
        //  使用异步编排来完成！
        //  获取CompletableFuture结果集的集合
        ArrayList<String> errorList = new ArrayList<>();
        //声明一个异步编排的集合
        ArrayList<CompletableFuture> futureList = new ArrayList<>();
        //表示获取订单的明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {



            //  开启异步编排!!!!!!!!!!!
            //  验证库存
            CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                boolean res = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                //  res = true 表示有库存，fasle 表示库存不足
                if (!res) {
                    // 提示消息
                    //  return Result.fail().message(orderDetail.getSkuName()+"库存不足！");
                    errorList.add(orderDetail.getSkuName() + "库存不足！");
                }
            }, threadPoolExecutor);
            //  将验证价格的线程添加到集合
            futureList.add(completableFuture);

            //验证价格
            CompletableFuture<Void> priceCompletableFuture = CompletableFuture.runAsync(() -> {
                // 获取实时价格
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                // 做对比
                if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                    //  价格有变动，那么应该将原来的价格进行修改。
                    //  购物车.loadCartCache();    将其放入feign-client 中。
                    //  提示消息
                    cartFeignClient.loadCartCache(userId);
                    errorList.add(orderDetail.getSkuName() + "价格有变动！");
                }
            },threadPoolExecutor);
            // 将验证的价格的线程添加到集合
            futureList.add(priceCompletableFuture);
        }
        //  CompletableFuture 组合
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();







        //判断结果
        if (errorList.size()>0){
            //说明有错误
            //  如果有价格变动，则提示价格变动。如果有库存不足，则需要提示库存不足！
            //  skuId29库存不足 ,价格有变动！
            return Result.fail().message(StringUtils.join(errorList,","));

        }


        // 验证通过，保存订单！
        Long orderId = orderService.saveOrderInfo(orderInfo);//保存订单
        return Result.ok(orderId);
    }
//========================支付=======================================
    /**
     * 内部调用获取订单
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId){
        return orderService.getOrderInfo(orderId);
    }


}
