package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.SkuSaleAttrValue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Map;

public interface SkuSaleAttrValueMapper extends BaseMapper<SkuSaleAttrValue> {
    //根据spuid查询map的集合数据
    List<Map> selectSaleAttrValuesBySpu(Long spuId);
}
