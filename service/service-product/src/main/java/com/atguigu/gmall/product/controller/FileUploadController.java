package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import org.apache.commons.io.FilenameUtils;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient1;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("admin/product")
public class FileUploadController {
    // 知道当前上传的服务器地址,将地址放在了配置文件，软编码。
    @Value("${fileServer.url}")
    private String fileUrl;// fileUrl=http://192.168.200.128:8080/

    //文件上传
    @RequestMapping("fileUpload")
    public Result fileUpload(MultipartFile file) throws Exception {
        // 配置了一个tracker.conf , 记录tracker_server
        // 读取到当前的配置文件

        String configFile = this.getClass().getResource("/tracker.conf").getFile();
        // 声明一个path,存储用户上传之后的地址 xxx/xxx/xxx.jpg
        String path="";
        //判断是否读取到
        if(configFile!=null){
            /*
                github,baidu.com
                1.  初始化
                2.  创建tracker
                3.  创建storage
                4.  上传
             */
            //初始化
            ClientGlobal.init(configFile);
            //创建tracker
            TrackerClient trackerClient = new TrackerClient();
            //trackerClient获取连接 ,获取trackerServer
            TrackerServer trackerServer = trackerClient.getConnection();

            //创建storage
            StorageClient1 storageClient1 = new StorageClient1(trackerServer, null);
            //上传
            //上传的时候需要获取当前文件的后缀名 zly.jpg;
            // extName = .jpg uuid123.jpg
            String extName = FilenameUtils.getExtension(file.getOriginalFilename());
            path = storageClient1.upload_appender_file1(file.getBytes(), extName, null);
            System.out.println("文件上传路径 ：\t"+path);
        }

        // 返回图片的路径
        // http://192.168.200.128:8080/xxx/xxx/xxx.jpg; my：好像是虚拟机中用docker安装的fastdfs容器的地址
        return Result.ok(fileUrl+path);

    }
}
