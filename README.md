# dataSpace

Minimal Spring Boot datasource wiring for Dameng (DM).

## What is included

- `spring-boot-starter-jdbc` is added for Spring Boot datasource auto-configuration.
- Default startup keeps datasource auto-configuration disabled, so the app can boot without DB settings.
- `dm` profile enables Dameng datasource properties in `src/main/resources/application-dm.properties`.
- A test-only H2 dependency is prepared to validate datasource auto-configuration in tests.
- 企业级文件平台：分片上传、断点续传、MD5秒传、AES加密存储、下载自动解密。

## Files

- `src/main/resources/application.properties`
  - default startup
  - datasource auto-configuration disabled
  - `file.storage.*` 文件平台工作空间配置
- `src/main/resources/application-dm.properties`
  - Dameng JDBC settings
- `src/main/resources/db/migration/V9__create_file_platform.sql`
  - 文件管理表/上传会话表/分片表
- `pom.xml`
  - JDBC starter

## 文件平台配置

可以通过环境变量覆盖：

- `FILE_STORAGE_WORKSPACE`：文件工作空间根目录
- `FILE_STORAGE_CHUNK_DIR`：分片目录（默认 `chunks`）
- `FILE_STORAGE_OBJECT_DIR`：加密对象目录（默认 `objects`）
- `FILE_STORAGE_MASTER_KEY_BASE64`：32字节主密钥的 Base64

## 文件平台接口（核心）

- `POST /file/platform/init`：初始化上传，会返回 `uploadId` 与已上传分片列表
- `POST /file/platform/chunk`：上传单个分片（支持幂等）
- `POST /file/platform/merge`：合并分片并加密存储
- `GET /file/platform/upload/{uploadId}/status`：查询断点续传状态
- `GET /file/platform/list`：查询文件管理表
- `GET /file/platform/download/{fileId}`：自动解密下载

## How to run without a database

The default configuration does not create a datasource.

```powershell
mvn spring-boot:run
```

## How to enable Dameng datasource

1. Make sure the Dameng JDBC driver jar is available to Maven.
2. Start the application with profile `dm`.
3. Provide connection parameters with environment variables or edit `application-dm.properties`.

Example environment variables:

```powershell
$env:DM_URL="jdbc:dm://127.0.0.1:5236"
$env:DM_USERNAME="SYSDBA"
$env:DM_PASSWORD="SYSDBA@root.123"
mvn spring-boot:run "-Dspring-boot.run.profiles=dm"
```

## Dameng driver note

This repository currently keeps only the driver class name and connection properties.
The actual Dameng JDBC dependency is not declared in `pom.xml` because the driver is often distributed through a private repository or a local jar instead of Maven Central.

If your organization provides a Maven coordinate, add it under `dependencies` in `pom.xml`.
If you only have a local jar, install it first, then add the dependency.

Example local install command (adjust file and version to your real package):

```powershell
mvn install:install-file `
  -DgroupId=com.dameng `
  -DartifactId=DmJdbcDriver18 `
  -Dversion=8.1.3.140 `
  -Dpackaging=jar `
  -Dfile="E:\drivers\DmJdbcDriver18-8.1.3.140.jar"
```

Then add a dependency like this to `pom.xml`:

- groupId: `com.dameng`
- artifactId: `DmJdbcDriver18`
- version: your installed version
- scope: `runtime`

## Common connection properties

Current `dm` profile uses:

- driver: `dm.jdbc.driver.DmDriver`
- url: `jdbc:dm://127.0.0.1:5236`
- username: `SYSDBA`
- password: `SYSDBA@root.123`

If your DBA requires a database name or extra URL parameters, update `DM_URL` accordingly.

## Verification status

Code changes are in place.
Dependency download was not verified in this environment because Maven Central access timed out.
Once your Maven repository access is available, run:

```powershell
mvn test
```
