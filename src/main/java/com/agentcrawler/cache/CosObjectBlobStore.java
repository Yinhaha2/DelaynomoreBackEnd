package com.agentcrawler.cache;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

public final class CosObjectBlobStore implements ObjectBlobStore {
    private final COSClient client;
    private final String bucket;

    public CosObjectBlobStore(COSClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public Optional<byte[]> get(String key) {
        COSObject object;
        try {
            object = client.getObject(bucket, key);
        } catch (CosServiceException ex) {
            // 404 当未命中：淘汰和读取会并发，缺对象应触发重爬，而不是把存储异常抛进用户请求。
            if (ex.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
        try (InputStream in = object.getObjectContent()) {
            return Optional.of(in.readAllBytes());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void put(String key, byte[] body) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(body.length);
        metadata.setContentType("application/json; charset=utf-8");
        client.putObject(bucket, key, new ByteArrayInputStream(body), metadata);
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(bucket, key);
        } catch (CosServiceException ex) {
            if (ex.getStatusCode() != 404) {
                throw ex;
            }
        }
    }
}
