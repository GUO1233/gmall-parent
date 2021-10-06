package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

public interface CartService {
    // 添加购物车 用户Id，商品Id，商品数量。
    void addToCart(Long skuId,String userId,Integer skuNum);
    /**
     * 通过用户Id 查询购物车列表
     * @param userId
     * @param userTempId
     * @return
     */

    List<CartInfo> getCartList(String userId,String userTempId);

    /**
     * 更新选中状态
     *
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId, Integer isChecked, Long skuId);


    void deleteCart(Long skuId, String userId);

    /**
     * 根据用户Id 查询购物车列表
     *
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);

    /**
     * 根据当前用户Id 查询商品的实时价格
     * @param userId
     * @return
     */
    List<CartInfo> loadCartCache(String userId);

}
