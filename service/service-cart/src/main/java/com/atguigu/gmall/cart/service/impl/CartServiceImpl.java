package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService{

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService cartAsyncService;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        /*
            数据是保存到mysql+redis
        1.  先判断购物车中是否有该商品
        2.  true:   有的话，数量相加
            false:  直接添加到购物车

            以上都是操作的数据库
        3.  将数据同步到缓存中！
         */
        //  分析使用那种数据类型 Hash hset(key,field,value)
        //  key=user:userId:cart field=skuId value=cartInfo.toString();
        //  ，第二个,使用的key 如何定义?

        String userCartkey = getUserCartkey(userId);

        // 如果当前缓存中没有用户的购物车
        if (!redisTemplate.hasKey(userCartkey)){
            //查询数据库并将数据加载到缓存中
            this.loadCartCache(userId);
        }

        //  select * from cart_info where user_id =? and sku_id=?
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("sku_id",skuId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(queryWrapper);
        //  判断
        if (null !=cartInfoExist){
            //  购物车中有当前商品
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            //  初始化当前的商品实时价格    skuInfo.price
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            //  更新数据库
           // cartInfoMapper.updateById(cartInfoExist);
            cartAsyncService.updateCartInfo(cartInfoExist);
            //  操作缓存
            //  redisTemplate.opsForHash().put(userCartkey,skuId,cartInfoExist);
        }else {
            //购物车中没有当前商品
            CartInfo cartInfo = new CartInfo();
            //  cart_info 表中的数据来源于谁?    service-product;
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);

            cartInfo.setUserId(userId);
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setSkuId(skuId);
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());

            //调用插入方法
//            cartInfoMapper.insert(cartInfo);
            cartAsyncService.saveCartInfo(cartInfo);
            // 废物利用：
            cartInfoExist=cartInfo;

            //  操作缓存
            //  redisTemplate.opsForHash().put(userCartkey,skuId,cartInfo);
        }

        //  操作缓存
        redisTemplate.boundHashOps(userCartkey).put(skuId.toString(),cartInfoExist);   //my:不管购物车里面是否有skuid商品，都要重新放入缓存因为商品的数量发生了变化
        //设置缓存的过期时间   setCartKeyExpire
        //  set key,value,px,1000,nx;   expire();
        setCartKeyExpire(userCartkey);
    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        // 什么一个返回的集合对象
        List<CartInfo> cartInfoList  = new ArrayList<>();
        //未登录：临时用户Id 获取未登录的购物车数据
        if (StringUtils.isEmpty(userId)) {
            cartInfoList = this.getCartList(userTempId);
            return cartInfoList;
        }

            /*
         1. 准备合并购物车
         2. 获取未登录的购物车数据
         3. 如果未登录购物车中有数据，则进行合并 合并的条件：skuId 相同 则数量相加，合并完成之后，删除未登录的数据！
         4. 如果未登录购物车没有数据，则直接显示已登录的数据
          */



        //已登录
        if (!StringUtils.isEmpty(userId)) {


            List<CartInfo> cartInfoArrayList = this.getCartList(userTempId);
            if (!CollectionUtils.isEmpty(cartInfoArrayList)){
                // 如果未登录购物车中有数据，则进行合并 合并的条件：skuId 相同
                cartInfoList= this.mergeToCartList(cartInfoArrayList,userId);
                //  合并完成之后，删除未登录购物车数据
                this.deleteCartList(userTempId);
            }
         /*   // 如果未登录购物车中没用数据！
            if (StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartInfoArrayList)){
                 cartInfoList = this.getCartList(userId);
            }*/


        }
       // return cartInfoList;

        return getCartList(userId);

    }

    //购物车勾选状态
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        //修改数据库
        cartAsyncService.checkCart(userId,isChecked,skuId);
        //修改缓存
        //定义key user:userId:cart
        String userCartkey = this.getUserCartkey(userId);
        BoundHashOperations<String, String, CartInfo> hashOperations  = redisTemplate.boundHashOps(userCartkey);//???
        //先获取用户选择的商品
        if (hashOperations.hasKey(skuId.toString())){
            CartInfo cartInfoUpd  = hashOperations.get(skuId.toString());
            // cartInfoUpd 写会缓存
            cartInfoUpd.setIsChecked(isChecked);

            // 更新缓存
            hashOperations.put(skuId.toString(), cartInfoUpd);
            // 设置过期时间
            this.setCartKeyExpire(userCartkey);


        }

    }

    @Override
    public void deleteCart(Long skuId, String userId) {
        String cartKey = getUserCartkey(userId);
        cartAsyncService.deleteCartInfo(userId, skuId);

        //获取缓存对象
        BoundHashOperations<String, String, CartInfo> hashOperations = redisTemplate.boundHashOps(cartKey);
        if (hashOperations.hasKey(skuId.toString())){
            hashOperations.delete(skuId.toString());
        }

    }



    //合并删除临时用户的数据
    private void deleteCartList(String userTempId) {
        // 删除数据库，删除缓存
        // delete from userInfo where userId = ?userTempId
        /*QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userTempId);
        cartInfoMapper.delete(queryWrapper);*/

        cartAsyncService.deleteCartInfo(userTempId);

        String userCartkey = getUserCartkey(userTempId);
        Boolean flag = redisTemplate.hasKey(userCartkey);
        if (flag){
            redisTemplate.delete(userCartkey);
        }

    }

    /**
     * 合并
     * @param cartInfoArrayList
     * @param userId
     * @return
     */
       /*
    demo1:
        登录：
            37 1
            38 1
        未登录：
            37 1
            38 1
            39 1
        合并之后的数据
            37 2
            38 2
            39 1
     demo2:
         未登录：
            37 1
            38 1
            39 1
            40  1
          合并之后的数据
            37 1
            38 1
            39 1
            40  1
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoArrayList, String userId) {

        //已登录购物车
        List<CartInfo> cartInfoListLogin  = this.getCartList(userId);
//        cartInfoListLogin.stream().collect(Collectors.toMap(CartInfo::getSkuId),cartInfo -> cartInfo)
        Map<Long, CartInfo> cartInfoMapLogin = cartInfoListLogin.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));//？？？？？


        for (CartInfo cartInfoNoLogin  : cartInfoArrayList) {
            Long skuId = cartInfoNoLogin.getSkuId();//获取未登录的购物车订单
            // 有更新数量
            if (cartInfoMapLogin.containsKey(skuId)){
                CartInfo cartInfoLogin = cartInfoMapLogin.get(skuId);
                //数量相加
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum() + cartInfoNoLogin.getSkuNum());

                // 未登录状态选中的商品！
                if (cartInfoNoLogin.getIsChecked().intValue() == 1) {
                    cartInfoLogin.setIsChecked(1);
                }

                // 更新数据库
                //cartInfoMapper.updateById(cartInfoLogin);
                cartAsyncService.updateCartInfo(cartInfoLogin);
            } else {
                cartInfoNoLogin.setUserId(userId);
                //cartInfoMapper.insert(cartInfoNoLogin);
                cartAsyncService.saveCartInfo(cartInfoNoLogin);
            }

        }
        // 汇总数据 37 38 39
        List<CartInfo> cartInfoList = loadCartCache(userId); // 数据库中的数据
        
        return cartInfoList;
    }

    private List<CartInfo> getCartList(String userId) {
        //声明一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();
        if (StringUtils.isEmpty(userId)){
            return cartInfoList;
        }
        /*
        1.  根据用户Id 查询 {先查询缓存，缓存没有，再查询数据库}
        */
        // 定义key user:userId:cart
        String cartkey = this.getUserCartkey(userId);
        //获取数据
        cartInfoList = redisTemplate.opsForHash().values(cartkey);
        if (!CollectionUtils.isEmpty(cartInfoList)){
            // 购物车列表显示有顺序：按照商品的更新时间 降序
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    // str1 = ab str2 = ac;
                    return o1.getId().toString().compareTo(o2.getId().toString());
                }
            });
            return cartInfoList;
        }else {
            //缓存中没有数据
            cartInfoList = loadCartCache(userId);
            return cartInfoList;

        }

    }

    /**
     * 通过userId 查询购物车并放入缓存！
     * @param userId
     * @return
     */
    public List<CartInfo> loadCartCache(String userId) {
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);

        List<CartInfo> cartInfoList = cartInfoMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(cartInfoList)){
            return cartInfoList;
        }
        // 将数据库中的数据查询并放入缓存
        HashMap<String, CartInfo> map = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(cartInfo.getSkuId());//CartInfo实体类所对应的表中没有实时价格这个字段
            cartInfo.setSkuPrice(skuPrice);
            map.put(cartInfo.getSkuId().toString(),cartInfo);
        }
        // 定义key user:userId:cart
        String userCartkey = this.getUserCartkey(userId);
        redisTemplate.opsForHash().putAll(userCartkey,map);
        //设置过期时间
        this.setCartKeyExpire(userCartkey);
        return cartInfoList;

    }

    //  设置购物车的过期时间
    private void setCartKeyExpire(String userCartkey) {
        redisTemplate.expire(userCartkey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    //  获取购物车的key
    private String getUserCartkey(String userId) {
        String cartKey= RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
        return cartKey;
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        // 定义key user:userId:cart
        String cartKey = this.getUserCartkey(userId);
        List<CartInfo> cartCachInfoList = redisTemplate.opsForHash().values(cartKey);
        if (null != cartCachInfoList && cartCachInfoList.size() > 0) {
            for (CartInfo cartInfo : cartCachInfoList) {
                // 获取选中的商品！
                if (cartInfo.getIsChecked().intValue() == 1) {
                    cartInfoList.add(cartInfo);
                }
            }
        }
        return cartInfoList;
    }
}
