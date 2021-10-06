package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface BaseTrademarkService extends IService<BaseTrademark> {
    //查询
    //查询所有的品牌数据，并且带有分页
    IPage<BaseTrademark> selectPage(Page<BaseTrademark> baseTrademarkPageParam);

    // 删除

    // 添加

    // 修改
}
