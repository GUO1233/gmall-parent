package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ListFeignClient listFeignClient;

    // 获取我们自己注入的线程池
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
          /*
        1，Sku基本信息
                 key  ---- value
        2，Sku图片信息
                 key ----- value
        3，Sku分类信息
                 key ----- value
        4，Sku销售属性相关信息
                 key ----- value
        5，Sku价格信息（平台可以单独修改价格，sku后续会放入缓存，为了回显最新价格，所以单独获取）、
                 key ----- value
     */
        // 需要将串行化改为并行化！
        // 组合任务allOf
        HashMap<String, Object> result = new HashMap<>();
        // 获取skuInfo ,skuImage 数据
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            // 将skuInfo 存储到map 中!
            result.put("skuInfo", skuInfo);
            return skuInfo;
        },threadPoolExecutor);
        //  SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        // 获取分类数据
        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            //存储数据
            result.put("categoryView", categoryView);
        },threadPoolExecutor);
        // BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());


        // 获取销售属性数据

        CompletableFuture<Void> spuSaleAttrCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
            result.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
        },threadPoolExecutor);

        // List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());


        // 获取价格
        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            result.put("price", skuPrice);
        },threadPoolExecutor);
        //BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);


        // 获取销售属性值与skuId 组成的map数据。
        CompletableFuture<Void> mapJsonCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            String mapJson = JSON.toJSONString(skuValueIdsMap);
            // 页面需要获取到Json 字符串
            result.put("valuesSkuJson", mapJson);
        },threadPoolExecutor);

      /*  CompletableFuture<Void> valuesSkuJson = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
                    Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
                    String mapJson = JSON.toJSONString(skuValueIdsMap);
                    // 页面需要获取到Json 字符串
                    result.put("valuesSkuJson", mapJson);
                })*/
//        Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
                // 我们跟前台商量需要在页面中存储一个json 字符串。
//        String mapJson = JSON.toJSONString(skuValueIdsMap);

                // 数据存储
                // 数据获取到之后，需要给页面展示！所以，我们必须要知道页面获取后台的key 是什么?
//        result.put("skuInfo",skuInfo);
//        result.put("categoryView",categoryView);
//        result.put("price",skuPrice);
//        // 页面需要获取到Json 字符串
//        result.put("valuesSkuJson",mapJson);
//        result.put("spuSaleAttrList",spuSaleAttrListCheckBySku);

        //调用商品的热度排名
        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);

        //进行多任务组合：allof
        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                categoryViewCompletableFuture,
                spuSaleAttrCompletableFuture,
                skuPriceCompletableFuture,
                mapJsonCompletableFuture,
                incrHotScoreCompletableFuture
                ).join();
        return result;
    }
}
