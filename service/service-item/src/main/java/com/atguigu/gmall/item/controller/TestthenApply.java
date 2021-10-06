package com.atguigu.gmall.item.controller;


import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TestthenApply {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                System.out.println(Thread.currentThread().getName() + "\t completableFuture");
                //int i = 10 / 0;
                return 1024;
            }
        }).thenApply(new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer o) {
                System.out.println("thenApply方法，上次返回结果：" + o);
                return  o * 2;
            }
        }).whenComplete(new BiConsumer<Integer, Throwable>() {
            @Override
            public void accept(Integer o, Throwable throwable) {
                System.out.println("-------o=" + o);
                System.out.println("-------throwable=" + throwable);
            }
        }).exceptionally(new Function<Throwable, Integer>() {
            @Override
            public Integer apply(Throwable throwable) {
                System.out.println("throwable=" + throwable);
                return 6666;
            }
        });
        System.out.println(future.get());
        //============================================================
        System.out.println("==============================");
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(50, 500, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10000));
        // 线程1执行返回的结果：hello
        CompletableFuture<String> futureA = CompletableFuture.supplyAsync(() -> "hello");

        // 线程2 获取到线程1执行的结果
        CompletableFuture<Void> futureB = futureA.thenAcceptAsync((s) -> {
            delaySec(3);
            printCurrTime(s+" 第一个线程");
        }, threadPoolExecutor);

        CompletableFuture<Void> futureC = futureA.thenAcceptAsync((s) -> {
            delaySec(1);
            printCurrTime(s+" 第二个线程");
        }, threadPoolExecutor);
    }



    private static void printCurrTime(String str) {
        System.out.println(str);
    }

    private static void delaySec(int i) {
        try {
            Thread.sleep(i*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
