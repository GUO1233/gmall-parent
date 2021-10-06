package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "品牌管理")
@RestController
@RequestMapping("admin/product/baseTrademark")
public class BaseTrademarkController {
    @Autowired
    private BaseTrademarkService baseTrademarkService;

    @GetMapping("{page}/{limit}")
    public Result getBaseTrademarkList(@PathVariable Long page,
                                       @PathVariable Long limit){
        Page<BaseTrademark> baseTrademarkPageParam = new Page<>(page, limit);
        IPage<BaseTrademark> baseTrademarkIPage = baseTrademarkService.selectPage(baseTrademarkPageParam);
        return  Result.ok(baseTrademarkIPage);
    }


    //添加
    @PostMapping("save")
    public Result saveBaseTradmark(@RequestBody BaseTrademark baseTrademark){
        //调用方法
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }

    //修改
    @PutMapping("update")
    public Result updateBaseTradmark(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.updateById(baseTrademark);
        return Result.ok();
    }

    //删除
    @DeleteMapping("remove/{id}")
    public Result removeBaseTrademark(@PathVariable Long id){
        baseTrademarkService.removeById(id);
        return Result.ok();
    }

    //根据id回显品牌数据
    @GetMapping("get/{id}")
    public Result getBaseTrademark(@PathVariable Long id){

        BaseTrademark baseTrademark = baseTrademarkService.getById(id);
        return Result.ok(baseTrademark);
    }
    //========================================================
    @GetMapping("getTrademarkList")
    public Result getTrademarkList(){
        List<BaseTrademark> trademarkList = baseTrademarkService.list(null);
        return Result.ok(trademarkList);
    }
}
