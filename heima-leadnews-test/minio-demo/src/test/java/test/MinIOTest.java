package test;

import com.heima.file.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;

@SpringBootTest(classes = MinIOTest.class)
public class MinIOTest {
    public static void main(String[] args) throws Exception {

    }
}
