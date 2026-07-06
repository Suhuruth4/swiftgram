package com.cove.server.storage;

import com.cove.server.config.AppProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class StorageService {
    private final MinioClient minioClient;
    private final AppProperties props;

    public StorageService(MinioClient minioClient, AppProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    public void putObject(String objectKey, InputStream input, long size, String contentType) throws Exception {
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(props.getStorage().getBucket())
                .object(objectKey)
                .stream(input, size, -1)
                .contentType(contentType)
                .build()
        );
    }

    public InputStream getObject(String objectKey) throws Exception {
        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(props.getStorage().getBucket())
                .object(objectKey)
                .build()
        );
    }
}
