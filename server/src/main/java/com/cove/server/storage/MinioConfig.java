package com.cove.server.storage;

import com.cove.server.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean
    public MinioClient minioClient(AppProperties props) throws Exception {
        MinioClient client = MinioClient.builder()
            .endpoint(props.getStorage().getEndpoint())
            .credentials(props.getStorage().getAccessKey(), props.getStorage().getSecretKey())
            .build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.getStorage().getBucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(props.getStorage().getBucket()).build());
        }
        return client;
    }
}
