package com.gijela.morpheus.chat.adapter.storage;

import com.gijela.morpheus.chat.config.ChatModuleProperties;
import com.gijela.morpheus.chat.domain.vo.ChatContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;

public class ObjectStorageGateway {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ChatModuleProperties properties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectStorageGateway(ChatModuleProperties properties, OkHttpClient okHttpClient) {
        this.properties = properties;
        this.okHttpClient = okHttpClient;
    }

    public Map<String, Object> upload(ChatContext context, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String objectKey = buildObjectKey(context, extension);
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }

        String mode = properties.getStorage().getMode();
        if (mode != null && "s3".equalsIgnoreCase(mode)) {
            return uploadWithS3(context, fileBytes, contentType, originalFilename, objectKey);
        }
        return uploadWithGateway(context, fileBytes, contentType, originalFilename, objectKey);
    }

    public List<Map<String, Object>> listBuckets() {
        ensureS3Mode();
        HttpUrl endpoint = buildS3EndpointUrl();
        S3CallResult result = executeS3SignedRequest("GET", endpoint, null, null);
        if (!result.success()) {
            throw new RuntimeException("S3 列桶失败: status=" + result.status() + ", body=" + clip(result.body()));
        }
        List<String> names = extractXmlTagValues(result.body(), "Name");
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            buckets.add(row);
        }
        return buckets;
    }

    public Map<String, Object> createBucket(String bucket) {
        ensureS3Mode();
        String normalized = normalizeBucketName(bucket);
        HttpUrl url = buildS3BucketUrl(normalized);
        S3CallResult result = executeS3SignedRequest("PUT", url, new byte[0], "application/octet-stream");
        if (!result.success() && result.status() != 409) {
            throw new RuntimeException("S3 建桶失败: status=" + result.status() + ", body=" + clip(result.body()));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bucket", normalized);
        map.put("status", result.status() == 409 ? "exists" : "created");
        return map;
    }

    public Map<String, Object> deleteBucket(String bucket) {
        ensureS3Mode();
        String normalized = normalizeBucketName(bucket);
        HttpUrl url = buildS3BucketUrl(normalized);
        S3CallResult result = executeS3SignedRequest("DELETE", url, null, null);
        if (!result.success()) {
            throw new RuntimeException("S3 删除桶失败: status=" + result.status() + ", body=" + clip(result.body()));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bucket", normalized);
        map.put("status", "deleted");
        return map;
    }

    public List<Map<String, Object>> listObjects(String bucket, String prefix, Integer maxKeys) {
        ensureS3Mode();
        String normalized = normalizeBucketName(bucket);
        HttpUrl.Builder builder = buildS3BucketUrl(normalized).newBuilder();
        builder.addQueryParameter("list-type", "2");
        if (prefix != null && !prefix.isBlank()) {
            builder.addQueryParameter("prefix", prefix);
        }
        int limit = maxKeys == null || maxKeys <= 0 ? 100 : Math.min(maxKeys, 1000);
        builder.addQueryParameter("max-keys", String.valueOf(limit));
        S3CallResult result = executeS3SignedRequest("GET", builder.build(), null, null);
        if (!result.success()) {
            throw new RuntimeException("S3 列对象失败: status=" + result.status() + ", body=" + clip(result.body()));
        }

        List<String> keys = extractXmlTagValues(result.body(), "Key");
        List<String> sizes = extractXmlTagValues(result.body(), "Size");
        List<String> lastModified = extractXmlTagValues(result.body(), "LastModified");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (key == null || key.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", normalized);
            row.put("key", key);
            row.put("size", i < sizes.size() ? parseLong(sizes.get(i)) : null);
            row.put("lastModified", i < lastModified.size() ? lastModified.get(i) : null);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(item -> String.valueOf(item.getOrDefault("key", ""))));
        return rows;
    }

    public Map<String, Object> deleteObject(String bucket, String objectKey) {
        ensureS3Mode();
        String normalizedBucket = normalizeBucketName(bucket);
        String normalizedKey = normalizeObjectKey(objectKey);
        HttpUrl url = buildS3BucketUrl(normalizedBucket).newBuilder().addEncodedPathSegments(normalizedKey).build();
        S3CallResult result = executeS3SignedRequest("DELETE", url, null, null);
        if (!result.success()) {
            throw new RuntimeException("S3 删除对象失败: status=" + result.status() + ", body=" + clip(result.body()));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bucket", normalizedBucket);
        map.put("objectKey", normalizedKey);
        map.put("status", "deleted");
        return map;
    }

    public Map<String, Object> uploadObject(String bucket, String objectKey, MultipartFile file) {
        ensureS3Mode();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String normalizedBucket = normalizeBucketName(bucket);
        String normalizedKey = normalizeObjectKey(objectKey);
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }
        HttpUrl url = buildS3BucketUrl(normalizedBucket).newBuilder().addEncodedPathSegments(normalizedKey).build();
        S3CallResult result = executeS3SignedRequest("PUT", url, fileBytes, contentType);
        if (!result.success()) {
            throw new RuntimeException("S3 上传对象失败: status=" + result.status() + ", body=" + clip(result.body()));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bucket", normalizedBucket);
        map.put("objectKey", normalizedKey);
        map.put("fileName", file.getOriginalFilename());
        map.put("size", (long) fileBytes.length);
        map.put("contentType", contentType);
        map.put("provider", properties.getStorage().getProvider());
        return map;
    }

    public DownloadedObject downloadObject(String bucket, String objectKey) {
        ensureS3Mode();
        String normalizedBucket = normalizeBucketName(bucket);
        String normalizedKey = normalizeObjectKey(objectKey);
        HttpUrl url = buildS3BucketUrl(normalizedBucket).newBuilder().addEncodedPathSegments(normalizedKey).build();
        return executeS3SignedDownload(url);
    }

    private Map<String, Object> uploadWithGateway(ChatContext context,
                                                  byte[] fileBytes,
                                                  String contentType,
                                                  String originalFilename,
                                                  String objectKey) {

        RequestBody fileBody;
        fileBody = RequestBody.create(fileBytes, MediaType.parse(contentType));

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("bucket", properties.getStorage().getBucket())
                .addFormDataPart("objectKey", objectKey)
                .addFormDataPart("tenantId", normalize(context.tenantId()))
                .addFormDataPart("sessionId", normalize(context.sessionId()))
                .addFormDataPart("file", originalFilename, fileBody)
                .build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(buildUploadUrl())
                .post(requestBody);
        String authorization = resolveAuthorizationHeader();
        if (authorization != null && !authorization.isBlank()) {
            requestBuilder.header("Authorization", authorization);
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("对象存储上传失败: status=" + response.code() + ", body=" + clip(body));
            }
            Map<String, Object> remote = body == null || body.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(body, new TypeReference<>() {
                    });
            Map<String, Object> result = new HashMap<>();
            result.put("bucket", remote.getOrDefault("bucket", properties.getStorage().getBucket()));
            result.put("objectKey", remote.getOrDefault("objectKey", objectKey));
            result.put("fileName", remote.getOrDefault("fileName", originalFilename));
            result.put("size", remote.getOrDefault("size", (long) fileBytes.length));
            result.put("provider", remote.getOrDefault("provider", properties.getStorage().getProvider()));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("对象存储调用失败", e);
        }
    }

    private Map<String, Object> uploadWithS3(ChatContext context,
                                             byte[] fileBytes,
                                             String contentType,
                                             String originalFilename,
                                             String objectKey) {
        String accessKey = properties.getStorage().getAccessKey();
        String secretKey = properties.getStorage().getSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("S3 模式必须配置 accessKey/secretKey");
        }

        String region = properties.getStorage().getRegion();
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }

        HttpUrl objectUrl = buildS3ObjectUrl(objectKey);
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
                .format(ZonedDateTime.now(ZoneOffset.UTC));
        String dateStamp = amzDate.substring(0, 8);
        String payloadHash = sha256Hex(fileBytes);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = "host:" + hostHeader(objectUrl) + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String canonicalRequest = "PUT\n"
                + objectUrl.encodedPath() + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        signingKey = hmacSha256(signingKey, region);
        signingKey = hmacSha256(signingKey, "s3");
        signingKey = hmacSha256(signingKey, "aws4_request");
        String signature = bytesToHex(hmacSha256(signingKey, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Request request = new Request.Builder()
                .url(objectUrl)
                .put(RequestBody.create(fileBytes, MediaType.parse(contentType)))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("S3 上传失败: status=" + response.code() + ", body=" + clip(body));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("bucket", properties.getStorage().getBucket());
            result.put("objectKey", objectKey);
            result.put("fileName", originalFilename);
            result.put("size", (long) fileBytes.length);
            result.put("provider", properties.getStorage().getProvider());
            result.put("tenantId", normalize(context.tenantId()));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("S3 上传调用失败", e);
        }
    }

    private HttpUrl buildUploadUrl() {
        String endpoint = properties.getStorage().getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("对象存储 endpoint 未配置");
        }
        HttpUrl base = HttpUrl.parse(endpoint);
        if (base == null) {
            throw new IllegalStateException("对象存储 endpoint 非法: " + endpoint);
        }
        String uploadPath = properties.getStorage().getUploadPath();
        if (uploadPath == null || uploadPath.isBlank()) {
            uploadPath = "/api/v1/storage/objects/upload";
        }
        String[] segments = uploadPath.replaceFirst("^/", "").split("/");
        HttpUrl.Builder builder = base.newBuilder();
        for (String segment : segments) {
            if (!segment.isBlank()) {
                builder.addPathSegment(segment);
            }
        }
        return builder.build();
    }

    private HttpUrl buildS3ObjectUrl(String objectKey) {
        String bucket = normalizeBucketName(properties.getStorage().getBucket());
        HttpUrl.Builder builder = buildS3BucketUrl(bucket).newBuilder();
        for (String seg : objectKey.split("/")) {
            if (!seg.isBlank()) {
                builder.addPathSegment(seg);
            }
        }
        return builder.build();
    }

    private String buildObjectKey(ChatContext context, String extension) {
        return String.format("%s/%s/%s/%s%s",
                normalize(context.tenantId()),
                normalize(context.sessionId()),
                LocalDate.now().format(DATE_FORMATTER),
                UUID.randomUUID(),
                extension);
    }

    private String clip(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return ".bin";
        }
        int index = filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index) : ".bin";
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }

    private String hostHeader(HttpUrl url) {
        boolean isDefaultPort = ("http".equalsIgnoreCase(url.scheme()) && url.port() == 80)
                || ("https".equalsIgnoreCase(url.scheme()) && url.port() == 443);
        if (isDefaultPort) {
            return url.host();
        }
        return url.host() + ":" + url.port();
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 计算失败", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String resolveAuthorizationHeader() {
        String authToken = properties.getStorage().getAuthToken();
        if (authToken != null && !authToken.isBlank()) {
            return authToken;
        }
        String accessKey = properties.getStorage().getAccessKey();
        String secretKey = properties.getStorage().getSecretKey();
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            return Credentials.basic(accessKey, secretKey);
        }
        return null;
    }

    private void ensureS3Mode() {
        String mode = properties.getStorage().getMode();
        if (mode == null || !"s3".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("当前存储模式不是 s3，请设置 CHAT_STORAGE_MODE=s3");
        }
    }

    private String normalizeBucketName(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket 不能为空");
        }
        return bucket.trim();
    }

    private String normalizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        return objectKey.trim().replaceAll("^/+", "");
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private HttpUrl buildS3EndpointUrl() {
        String endpoint = properties.getStorage().getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("对象存储 endpoint 未配置");
        }
        HttpUrl base = HttpUrl.parse(endpoint);
        if (base == null) {
            throw new IllegalStateException("对象存储 endpoint 非法: " + endpoint);
        }
        return base;
    }

    private HttpUrl buildS3BucketUrl(String bucket) {
        HttpUrl base = buildS3EndpointUrl();
        if (!properties.getStorage().isS3PathStyle()) {
            throw new IllegalStateException("当前仅支持 s3-path-style=true");
        }
        return base.newBuilder().addPathSegment(bucket).build();
    }

    private String canonicalQueryString(HttpUrl url) {
        if (url == null || url.querySize() == 0) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < url.querySize(); i++) {
            String name = url.queryParameterName(i);
            String value = url.queryParameterValue(i);
            String encodedName = percentEncode(name == null ? "" : name);
            String encodedValue = percentEncode(value == null ? "" : value);
            pairs.add(encodedName + "=" + encodedValue);
        }
        pairs.sort(String::compareTo);
        return String.join("&", pairs);
    }

    private String percentEncode(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append('%').append(String.format("%02X", b));
                }
            }
        }
        return sb.toString();
    }

    private S3CallResult executeS3SignedRequest(String method, HttpUrl url, byte[] payload, String contentType) {
        String accessKey = properties.getStorage().getAccessKey();
        String secretKey = properties.getStorage().getSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("S3 模式必须配置 accessKey/secretKey");
        }
        String region = properties.getStorage().getRegion();
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        byte[] body = payload == null ? new byte[0] : payload;
        String payloadHash = sha256Hex(body);
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
                .format(ZonedDateTime.now(ZoneOffset.UTC));
        String dateStamp = amzDate.substring(0, 8);

        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = "host:" + hostHeader(url) + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String canonicalRequest = method + "\n"
                + url.encodedPath() + "\n"
                + canonicalQueryString(url) + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        signingKey = hmacSha256(signingKey, region);
        signingKey = hmacSha256(signingKey, "s3");
        signingKey = hmacSha256(signingKey, "aws4_request");
        String signature = bytesToHex(hmacSha256(signingKey, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization);

        if (contentType != null && !contentType.isBlank()) {
            requestBuilder.header("Content-Type", contentType);
        }

        if ("PUT".equalsIgnoreCase(method)) {
            requestBuilder.put(RequestBody.create(body, MediaType.parse(contentType == null ? "application/octet-stream" : contentType)));
        } else if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.post(RequestBody.create(body, MediaType.parse(contentType == null ? "application/octet-stream" : contentType)));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            requestBuilder.delete();
        } else {
            requestBuilder.get();
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            return new S3CallResult(response.code(), responseBody);
        } catch (IOException e) {
            throw new RuntimeException("S3 调用失败", e);
        }
    }

    private DownloadedObject executeS3SignedDownload(HttpUrl url) {
        String accessKey = properties.getStorage().getAccessKey();
        String secretKey = properties.getStorage().getSecretKey();
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("S3 模式必须配置 accessKey/secretKey");
        }
        String region = properties.getStorage().getRegion();
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        String payloadHash = sha256Hex(new byte[0]);
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
                .format(ZonedDateTime.now(ZoneOffset.UTC));
        String dateStamp = amzDate.substring(0, 8);

        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = "host:" + hostHeader(url) + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String canonicalRequest = "GET\n"
                + url.encodedPath() + "\n"
                + canonicalQueryString(url) + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        signingKey = hmacSha256(signingKey, region);
        signingKey = hmacSha256(signingKey, "s3");
        signingKey = hmacSha256(signingKey, "aws4_request");
        String signature = bytesToHex(hmacSha256(signingKey, stringToSign));

        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Request request = new Request.Builder()
                .url(url)
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.bytes();
            if (!response.isSuccessful()) {
                String bodyText = new String(bytes, StandardCharsets.UTF_8);
                throw new RuntimeException("S3 下载对象失败: status=" + response.code() + ", body=" + clip(bodyText));
            }
            String contentType = response.header("Content-Type");
            return new DownloadedObject(contentType, bytes);
        } catch (IOException e) {
            throw new RuntimeException("S3 下载调用失败", e);
        }
    }

    private List<String> extractXmlTagValues(String xml, String tagName) {
        List<String> values = new ArrayList<>();
        if (xml == null || xml.isBlank() || tagName == null || tagName.isBlank()) {
            return values;
        }
        Pattern pattern = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private record S3CallResult(int status, String body) {
        private boolean success() {
            return status >= 200 && status < 300;
        }
    }

    public record DownloadedObject(String contentType, byte[] bytes) {
    }
}
