package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("api/cart")
public class CartApiController {
    @Autowired
    private CartService cartService;

    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)){
            // 获取临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.addToCart(skuId,userId,skuNum);
        return Result.ok();
    }

    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request){
        //获取用户ID
        String userId = AuthContextHolder.getUserId(request);
        //获取临时用户ID
        String userTempId = AuthContextHolder.getUserTempId(request);
        List<CartInfo> cartInfoList  = cartService.getCartList(userId, userTempId);
        return Result.ok(cartInfoList);

    }

    /**
     * 更新选中状态
     *
     * @param skuId
     * @param isChecked
     * @param request
     * @return
     */

    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable(value ="skuId") Long skuId,
                            @PathVariable(value = "isChecked") Integer isChecked,
                            HttpServletRequest request){

        //获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // update cartInfo set isChecked=? where  skuId = ? and userId=？
        if (StringUtils.isEmpty(userId)){
            // 未登录
            userId = AuthContextHolder.getUserTempId(request);
        }
        // 调用更新方法
        cartService.checkCart(userId, isChecked, skuId);
        return Result.ok();
    }

    /**
     * 删除
     *
     * @param skuId
     * @param request
     * @return
     */
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable("skuId") Long skuId,
                             HttpServletRequest request) {
        // 如何获取userId
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            // 获取临时用户Id
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.deleteCart(skuId, userId);
        return Result.ok();
    }

    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable(value = "userId") String userId) {
        return cartService.getCartCheckedList(userId);
    }

    /**
     * 提交订单，更新实时价格要远程调用使用
     * @param userId
     * @return
     */
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId) {
        cartService.loadCartCache(userId);
        return Result.ok();
    }
}
