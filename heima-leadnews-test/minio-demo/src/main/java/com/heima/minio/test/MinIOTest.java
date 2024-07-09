package com.heima.minio.test;

import com.heima.file.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@SpringBootApplication
public class MinIOTest {
    @Autowired
    private FileStorageService fileStorageService;

    public static void main(String[] args) throws Exception {
        FileInputStream fileInputStream = new FileInputStream("/Users/songtengxiao/Desktop/Playground/hm-news/root/minio/hello.html");
        // 获取MinIOClient对象
        MinioClient client = MinioClient.builder()
                .credentials("minio", "minio123")
                .endpoint("http://localhost:9000")
                .build();
        // 上传
        PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                .bucket("leadnews")
                .object("hello.html")
                .contentType("text/html")
                .stream(fileInputStream, fileInputStream.available(), -1)
                .build();
        client.putObject(putObjectArgs);
        System.out.println("http://localhost:9000/leadnews/hello.html");


        // start spring boot
        SpringApplication.run(MinIOTest.class, args);
    }

    private void test() throws FileNotFoundException {
        FileInputStream fileInputStream = new FileInputStream("/Users/songtengxiao/Desktop/Playground/hm-news/root/minio/hello.html");

        String path = fileStorageService.uploadHtmlFile("", "hello.html", fileInputStream);

        System.out.println(path);
    }
}
