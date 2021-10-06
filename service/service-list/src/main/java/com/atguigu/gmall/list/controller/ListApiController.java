package com.atguigu.gmall.list.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.Goods;
import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("api/list")
public class ListApiController {
    // 注入操作es的类API
    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    @Autowired
    private SearchService  searchService;

    // 自定义一个控制器url ，能够实现自动创建Index,Type,以及Type 对应的字段，数据类型。
    @GetMapping("inner/createIndex")
    public Result createIndex(){
        // 创建index，type
        restTemplate.createIndex(Goods.class);
        restTemplate.putMapping(Goods.class);

        return Result.ok();
    }

    /**
     * 上架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/upperGoods/{skuId}")
    public Result upperGoods(@PathVariable("skuId") Long skuId) {
        searchService.upperGoods(skuId);
        return Result.ok();
    }
    /**
     * 下架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/lowerGoods/{skuId}")
    public Result lowerGoods(@PathVariable("skuId") Long skuId) {
        searchService.lowerGoods(skuId);
        return Result.ok();
    }

    @GetMapping("inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable Long skuId){
        searchService.incrHotScore(skuId);
        return Result.ok();
    }
    //====================11day===================================
    @PostMapping
    public Result list(@RequestBody SearchParam searchParam) throws IOException {
        SearchResponseVo response  = searchService.search(searchParam);
        return Result.ok(response);
    }
    
}


