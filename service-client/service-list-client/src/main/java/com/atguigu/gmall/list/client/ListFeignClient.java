package com.atguigu.gmall.list.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;

@FeignClient(value = "service-list")
public interface ListFeignClient {
    @GetMapping("api/list/inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable Long skuId);

    /**
     * 上架商品
     * @param skuId
     * @return
     */
    @GetMapping("api/list/inner/upperGoods/{skuId}")
    public Result upperGoods(@PathVariable("skuId") Long skuId);


    /**
     * 下架商品
     * @param skuId
     * @return
     */
    @GetMapping("api/list/inner/lowerGoods/{skuId}")
    public Result lowerGoods(@PathVariable("skuId") Long skuId) ;

    /**
     * 搜索商品
     * @param searchParam
     * @return
     */

    @PostMapping("api/list")
    public Result list(@RequestBody SearchParam searchParam) ;
}
