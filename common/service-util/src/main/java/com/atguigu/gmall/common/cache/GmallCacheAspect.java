package com.atguigu.gmall.common.cache;


import com.atguigu.gmall.common.constant.RedisConst;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;


@Component
@Aspect
public class GmallCacheAspect {
    /*
     1. 只要发现某个方法上有GmallCache注解，那么就会将数据放入缓存{防止缓存击穿+缓存穿透}！
     */
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;


    /**
     * 返回值不确定！所以给Object
     * @param point point 获取方法执行前，执行后，执行的结果。
     * @return
     */
    @SneakyThrows
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint point){
        Object object = new Object();
        /*
        1.  先获取方法上的注解，获取注解的prefix
        2.  获取方法上传递的参数
        3.  组成缓存的key prefix + 方法上的参数
        4.  从缓存中获取数据，
            判断缓存是否有数据，没有则从数据库中获取，并放入缓存！
         */
        MethodSignature signature = (MethodSignature) point.getSignature();
        // 获取方法上的注解
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
        // 获取注解上的前缀
        String prefix = gmallCache.prefix();
        //  获取方法上的参数
        Object[] args = point.getArgs();

        // 设置缓存的key
        // key = skuPrice:[28]
        String key = prefix+ Arrays.asList(args).toString();

        // 获取缓存中的数据
        //object = cacheHit(key,signature);
        object = cacheHit(key);

        // object = cacheHit(key);

        // 逻辑判断
        if (object==null){
            // 制作分布式锁！
            String lockKey = key+":lock";
            // 调用redissonClient
            RLock lock = redissonClient.getLock(lockKey);
            // 试着上锁
            boolean flag = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
            // flag = true 表示上锁上成功！
            if (flag){
                try {
                    // 执行业务逻辑：从数据库中获取数据，并放入缓存！
                    object = point.proceed(point.getArgs());
                    // 判断查询的数据是否为空 , 防止缓存穿透
                    if (object==null){
                        Object object1 = new Object();
                        // 将空数据放入缓存 , 给缓存赋值的适合，将其转化字符串
                       // redisTemplate.opsForValue().set(key, JSON.toJSONString(object1),RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        redisTemplate.opsForValue().set(key, object1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        // 返回数据
                        return object1;
                    }
                    // 表示从缓存中获取到了数据
                    // redisTemplate.opsForValue().set(key, object,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    //redisTemplate.opsForValue().set(key, JSON.toJSONString(object),RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    redisTemplate.opsForValue().set(key, object,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    //  返回数据
                    return object;
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }else {
                // 睡眠
                Thread.sleep(1000);
               // return cacheHit(key,signature);
                return cacheHit(key);
                // return cacheAroundAdvice(point);
            }
        }
        return object;
    }

   //  从缓存中获取数据！
    private Object cacheHit(String key) {

        Object object = redisTemplate.opsForValue().get(key);
        if (object!=null){
            return object;
        }

        return null;
    }


    // 表示获取缓存中的数据！
/*    private Object cacheHit(String key,MethodSignature signature) {
        // 将缓存中的数据获取出来，变为字符串
        String object = (String) redisTemplate.opsForValue().get(key);
        // 获取的数不为空！
        if (!StringUtils.isEmpty(object)){
            // 获取方法的返回值类型！
            Class returnType = signature.getReturnType();
            // 如果执行的是getSkuPrice 那么这个返回值就是 ：BigDecimal
            // 如果执行的是getSpuSaleAttrListCheckBySku 那么返回值是： List<SpuSaleAttr>
            return  JSON.parseObject(object,returnType);
        }
        return null;
    }*/
}
