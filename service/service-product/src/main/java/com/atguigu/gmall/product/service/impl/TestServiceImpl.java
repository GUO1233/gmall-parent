package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.product.service.TestService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void testLock() {
        // // 创建锁：
        String skuId="25";
        String locKey ="lock:"+skuId;
        // 锁的是每个商品
        RLock lock = redissonClient.getLock(locKey);
        //开始枷锁
       // lock.lock();
        //lock.lock(10,TimeUnit.SECONDS);//加锁以后10秒钟自动解锁   // 无需调用unlock方法手动解锁
        boolean res=false;
        try {
            res = lock.tryLock(100, 10, TimeUnit.SECONDS);
            try {
                if(res){
                    String value = redisTemplate.opsForValue().get("num");
                    if (StringUtils.isBlank(value)){
                        return;
                    }
                    // 将value 变为int
                    int num = Integer.parseInt(value);
                    // 将num +1 放入缓存
                    redisTemplate.opsForValue().set("num",String.valueOf(++num));
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            } finally {
                //解锁
                lock.unlock();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 业务逻辑代码
        // 获取数据

        // 解锁：
       // lock.unlock();
    }

    @Override
    public String readLock() {

        //初始化读写锁
        RReadWriteLock readwriteLock = redissonClient.getReadWriteLock("readwriteLock");
        RLock rLock = readwriteLock.readLock();
        rLock.lock(10, TimeUnit.SECONDS);
        String msg = this.redisTemplate.opsForValue().get("msg");
        return msg;
    }

    @Override
    public String writeLock() {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock("readwriteLock");
        RLock rLock = readWriteLock.writeLock(); // 获取写锁

        rLock.lock(10, TimeUnit.SECONDS); // 加10s锁

        this.redisTemplate.opsForValue().set("msg", UUID.randomUUID().toString());

        //rLock.unlock(); // 解锁
        return "成功写入了内容。。。。。。";

    }



   /* @Override
    public synchronized void testLock() {
        String value = (String)this.redisTemplate.opsForValue().get("num");
        // 没有该值return
        if (StringUtils.isBlank(value)){
            return ;
        }
        // 有值就转成成int
        int num = Integer.parseInt(value);
        // 把redis中的num值+1
        this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

        // 1. 从redis中获取锁,setnx
        String uuid = UUID.randomUUID().toString();
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid,3, TimeUnit.SECONDS);
        if (lock) {
            // 查询redis中的num值
            String value = (String)this.redisTemplate.opsForValue().get("num");
            // 没有该值return
            if (StringUtils.isBlank(value)){
                return ;
            }
            // 有值就转成成int
            int num = Integer.parseInt(value);
            // 把redis中的num值+1
            this.redisTemplate.opsForValue().set("num", String.valueOf(++num));

            // 2. 释放锁 del
            if (uuid.equals((String)redisTemplate.opsForValue().get("lock"))){
                this.redisTemplate.delete("lock");
            }
            String script="if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 ";
            // 第一种传值
            // DefaultRedisScript<Object> redisScript = new DefaultRedisScript<>(script);
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            // 第二种传值
            redisScript.setScriptText(script);
            // 设置一下返回值类型 为Long
            // 因为删除判断的时候，返回的0,给其封装为数据类型。如果不封装那么默认返回String 类型，那么返回字符串与0 会有发生错误。
            redisScript.setResultType(Long.class);
            // 第一个要是script 脚本 ，第二个需要判断的key，第三个就是key所对应的值。
            redisTemplate.execute(redisScript, Arrays.asList("lock"),uuid);


        } else {
            // 3. 每隔1秒钟回调一次，再次尝试获取锁
            try {
                Thread.sleep(100);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


    }*/
}
