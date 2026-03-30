# dataSpace

Minimal Spring Boot datasource wiring for Dameng (DM).

## What is included

- `spring-boot-starter-jdbc` is added for Spring Boot datasource auto-configuration.
- Default startup keeps datasource auto-configuration disabled, so the app can boot without DB settings.
- `dm` profile enables Dameng datasource properties in `src/main/resources/application-dm.properties`.
- A test-only H2 dependency is prepared to validate datasource auto-configuration in tests.

## Files

- `src/main/resources/application.properties`
  - default startup
  - datasource auto-configuration disabled
- `src/main/resources/application-dm.properties`
  - Dameng JDBC settings
- `pom.xml`
  - JDBC starter

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

