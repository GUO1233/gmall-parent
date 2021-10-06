package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseSaleAttr;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/product")
public class SpuManageController {
    @Autowired
    private ManageService manageService;

    // http://api.gmall.com/admin/product/{page}/{limit}?category3Id=61
    // 使用spring mvc 对象传值。如果传递的参数与实体类的属性名一致，那么就可以使用对象
    @GetMapping("{page}/{size}")
    public Result getSpuInfoPage(@PathVariable Long page,
                                 @PathVariable Long size,
                                 SpuInfo spuInfo){
        Page<SpuInfo> spuInfoPage = new Page<>(page, size);
        IPage<SpuInfo> spuInfoPage1 = manageService.getSpuInfoPage(spuInfoPage, spuInfo);
        // 返回！
        return Result.ok(spuInfoPage1);

    }

    //================================================================
// 销售属性http://api.gmall.com/admin/product/baseSaleAttrList
    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){
        List<BaseSaleAttr> baseSaleAttrList = manageService.getBaseSaleAttrList();
        return Result.ok(baseSaleAttrList);
    }

    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        //调用服务层的保存方法
        manageService.saveSpuInfo(spuInfo);
        return Result.ok();
    }

    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo) {
        // 调用服务层
        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

}
