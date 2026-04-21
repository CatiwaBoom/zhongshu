# 文件模块开发手册

版本：1.0

说明：本手册描述项目中“文件模块（File Platform）”的架构、配置、核心实现、操作流程和常见排错方法。请将该文档放在项目文档目录以便团队使用。

目录
- 概要
- 设计目标与约定
- 依赖与配置
- 核心类与文件说明
- 数据库实体与表映射
- 上传流程（Init / Chunk / Merge / Complete）
- 加密与解密实现细节
- 存储抽象：`StorageService` 与 `MinioStorageService`
- API: Controller 与使用示例
- 测试与验证步骤
- 常见问题与排查（含 MinIO 签名、Spring bean 循环）
- 扩展点与待改进事项
- 附录：常用命令与示例

---

## 概要
文件模块负责用户文件的分片上传、合并、加密、存储和下载。当前实现采用 MinIO 作为对象存储（S3 兼容），并使用 AES-GCM 做文件加密（每文件生成 DEK 并用主密钥包装）。模块目标：

- 分片（chunk）上传全流式（不在服务器写临时分片文件）
- 分片上传时实时计算 MD5（Digest）并保存到 DB
- 合并后对明文进行 AES-GCM 加密，再通过 MinIO SDK 的 multipart 上传（支持大文件）
- 移除本地存储配置，使用系统临时目录仅用于合并产生的临时合并文件
- 所有注释与日志使用中文

## 设计目标与约定

- `storagePath` 采用语义化 URI：`minio://<bucket>/<objectName>`，用于在 DB 中记录对象位置。
- 分片上传（chunk）要求：接收到 `MultipartFile` 的 `InputStream` 直接传给 `StorageService.uploadRaw`，该方法返回分片 MD5（小写 hex）。
- 合并流程在服务器端生成一次 `merged.tmp` 临时文件用于校验完整文件 MD5；加密后写入临时文件并通过 MinIO 的 `uploadObject`（multipart）上传最终对象。

## 依赖与配置

关键依赖（已添加到 `pom.xml`）：
- `io.minio:minio`（MinIO Java SDK）
- `commons-io`（可选的 I/O 工具库）

主要配置项（示例，位于 `application.properties`）：

```
# 文件加解密主密钥（Base64，解码后必须为 32 字节）
file.storage.master-key-base64=${FILE_STORAGE_MASTER_KEY_BASE64:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}

# MinIO 配置
file.storage.minio.endpoint=${FILE_STORAGE_MINIO_ENDPOINT:http://127.0.0.1:9000}
file.storage.minio.access-key=${FILE_STORAGE_MINIO_ACCESS_KEY:admin}
file.storage.minio.secret-key=${FILE_STORAGE_MINIO_SECRET_KEY:minIO@root.123}
file.storage.minio.bucket=${FILE_STORAGE_MINIO_BUCKET:data-space}
file.storage.minio.secure=${FILE_STORAGE_MINIO_SECURE:false}
file.storage.minio.auto-create-bucket=${FILE_STORAGE_MINIO_AUTO_CREATE_BUCKET:true}
```

注意：容器化 MinIO 的环境变量名（如 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`）必须与应用配置一致。

## 核心类与文件说明

- `FilePlatformController.java`：REST API 层（初始化、分片上传、合并/完成、下载、删除）。
- `FilePlatformService` / `FilePlatformServiceImpl`：业务实现（上传会话、分片记录、合并、加密、最终上传、清理）。
- `FileCryptoService.java`：AES-GCM 加解密实现，提供基于文件与基于流的 API。
- `FileStorageProperties.java`：配置类（`file.storage` 前缀）。
- `StorageService`（抽象接口）与 `MinioStorageService`（MinIO 实现）：封装对象存储操作。
- DTO / Entity / Mapper：`InitUploadRequest/Response`、`CompleteUploadRequest`、`UploadStatusResponse`、`FileChunkEntity`（sys_file_chunk）、`FileUploadSessionEntity`（sys_file_upload_session）、`FileObjectEntity`（sys_file_object）。

## 数据库实体（重要字段）

- `sys_file_upload_session`（FileUploadSessionEntity）: `uploadId`, `fileMd5`, `fileName`, `fileSize`, `chunkSize`, `totalChunks`, `uploadedChunks`, `fileObjectId`。
- `sys_file_chunk`（FileChunkEntity）: `uploadId`, `chunkIndex`, `chunkSize`, `chunkMd5`（分片 MD5）。
- `sys_file_object`（FileObjectEntity）: `fileMd5`, `fileName`, `contentType`, `fileSize`, `chunkSize`, `totalChunks`, `storagePath`, `storageSize`, `cipherIv`, `wrapIv`, `wrappedDek` 等。

## 上传流程（端到端）

1. 初始化（POST `/file/platform/init`）
   - 前端提交 `InitUploadRequest`（fileName, fileMd5, fileSize, chunkSize, totalChunks）
   - 后端生成 `uploadId` 并写入 `sys_file_upload_session`。

2. 分片上传（POST `/file/platform/chunk`）
   - 参数：`uploadId`, `md5`（文件 MD5）, `chunkIndex`, `totalChunks`, `file`（MultipartFile）。
   - 实现：`MultipartFile.getInputStream()` 直接传给 `storageService.uploadRaw(in, objectName, size)`，无本地临时文件。
   - `uploadRaw` 在上传时通过 `DigestInputStream` 实时计算该分片 MD5 并返回，服务端记录 `FileChunkEntity` 并更新 `uploadedChunks`。

3. 合并 & 完成（POST `/file/platform/complete`）
   - 后端在系统临时目录创建 `merged.tmp`，按 `chunkIndex` 顺序调用 `storageService.downloadRaw(chunkObject)` 写入 `merged.tmp`。
   - 使用 `FileHashUtils.md5(merged.tmp)` 校验与客户端提供的 `fileMd5` 是否一致。
   - 使用 `FileCryptoService.encrypt(InputStream, OutputStream)` 将明文流加密至临时文件（例如 `merged.tmp.enc`），并记录 `cipherIv/wrapIv/wrappedDek`。
   - 通过 `MinioStorageService.uploadEncrypted`（可使用 `minioClient.uploadObject`）将加密文件 multipart 上传到 MinIO，返回 `storagePath = minio://bucket/object`。
   - 在 DB 写入 `FileObjectEntity`，清理 chunk 对象与 chunk DB 记录，以及本地临时文件。

4. 下载（GET `/file/platform/download/{fileId}`）
   - 后端解析 `storagePath`，使用 `storageService.downloadRaw(objectName)` 获取加密流，调用 `FileCryptoService.decrypt(encryptedIn, cipherIv, wrapIv, wrappedDek, response.getOutputStream())` 将解密数据直接写入 HTTP 响应流。

5. 删除（POST `/file/platform/delete/{fileId}`）
   - 删除对象存储中的 object（`storageService.delete(objectName)`）并删除 `sys_file_object` 记录。

## `FileCryptoService` 说明（加解密细节）

- 算法：AES-GCM (`AES/GCM/NoPadding`)，IV 长度 12 字节，TAG 长度 128 位。
- 主密钥（masterKey）：32 字节，Base64 存储在 `file.storage.master-key-base64`。
- 每文件生成随机 DEK（256 位），随机 file IV 与 wrap IV（各 12 字节）；使用主密钥对 DEK 包装得到 `wrappedDek`。
- 提供文件与流两套接口：
  - `encrypt(Path plainFile, Path encryptedFile)` / `decryptToStream(Path encryptedFile, ...)`
  - `encrypt(InputStream in, OutputStream out)` / `decrypt(InputStream encryptedIn, cipherIv, wrapIv, wrappedDek, OutputStream out)`（流式实现支持大文件）

注意：流式 `encrypt` 返回 `EncryptionResult`，包含 `cipherIv/wrapIv/wrappedDek` 与 `storageSize`（写入字节数）。

## 存储抽象：`StorageService` 与 `MinioStorageService`

- `StorageService`（接口）职责：
  - `uploadRaw(InputStream in, String objectName, long size)` -> 返回分片 MD5（hex）
  - `uploadEncrypted(Path encryptedFile, String objectName)` 或 `uploadEncrypted(InputStream in, String objectName, long size)` -> 返回 `storagePath`
  - `downloadRaw(String objectName)` -> InputStream
  - `delete(String objectName)`

- `MinioStorageService` 实现要点：
  - `uploadRaw`：在上传期间使用 `DigestInputStream` 计算 MD5，避免重复读取。使用 `PutObjectArgs.stream()` 上传。
  - `uploadEncrypted`：通常把加密输出写入本地临时文件，然后调用 `minioClient.uploadObject(UploadObjectArgs.builder()...)`，触发 SDK 的 multipart 上传以支持大文件。
  - `downloadRaw`：使用 `minioClient.getObject(GetObjectArgs.builder()...)` 获取流（调用方负责关闭）。
  - `delete`：使用 `minioClient.removeObject(RemoveObjectArgs.builder()...)`。

实现细节与注意：
- `MinioConfig` 中构建 `MinioClient` 时需要对 `endpoint` 做鲁棒解析（支持带 scheme 的 URL 或单独 host/port + secure），以避免签名不匹配问题。
- 自动建桶逻辑应在 `ApplicationReadyEvent` 中执行以避免 Spring 的 bean 创建期循环依赖。

## API 使用示例（curl）

初始化：
```
curl -X POST http://localhost:8080/file/platform/init -H "Content-Type: application/json" -d '{"fileName":"large.bin","fileSize":12345678,"fileMd5":"<md5>","chunkSize":5242880,"totalChunks":3}'
```

分片上传：
```
curl -X POST http://localhost:8080/file/platform/chunk \
  -F uploadId=<uploadId> \
  -F md5=<fileMd5> \
  -F chunkIndex=1 \
  -F totalChunks=3 \
  -F file=@part1.bin
```

完成合并：
```
curl -X POST http://localhost:8080/file/platform/complete -H "Content-Type: application/json" -d '{"uploadId":"...","fileMd5":"...","fileName":"large.bin","totalChunks":3,"contentType":"application/octet-stream"}'
```

下载：
```
curl -v http://localhost:8080/file/platform/download/<fileId> -o large.bin
```

## 测试与验证步骤

1. 启动 MinIO（示例 Docker 命令）
```
docker run -d --name minio --restart=always \
  -p 9000:9000 -p 9001:9001 \
  -v E:\DevelopKits\minIO\data:/data \
  -e "MINIO_ROOT_USER=admin" \
  -e "MINIO_ROOT_PASSWORD=minIO@root.123" \
  registry.cn-hangzhou.aliyuncs.com/aneasos/minio:RELEASE.2021-06-17T00-10-41Z server /data --console-address ":9001"
```

2. 使用 mc 验证连接：
```
mc alias set myminio http://127.0.0.1:9000 admin minIO@root.123
mc ls myminio
```

3. 启动应用：
```
mvn -DskipTests spring-boot:run
# 或
java -jar target/dataSpace-1.0-SNAPSHOT.jar
```

4. 执行完整上传流程并验证数据库与对象存储。


