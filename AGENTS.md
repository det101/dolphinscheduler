# DolphinScheduler Development Notes

## Cursor Cloud specific instructions

### Java & Build

- **JDK 8 required**: The project targets `java.version=1.8`. Install `openjdk-8-jdk` and set `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64`. The pre-installed JDK 21 will cause runtime issues with Spring Boot 2.6.1.
- **Maven Wrapper**: Use `./mvnw` (Maven 3.8.4 bundled). No system Maven needed.
- **Full build**: `./mvnw clean install -DskipTests -Dspotless.skip=true -Dmaven.javadoc.skip=true -T 2C` (~2.5 min).
- **Backend lint (Spotless)**: `./mvnw spotless:check` or `./mvnw spotless:apply` to auto-fix.
- **Backend tests**: `./mvnw test -pl <module> -Dspotless.skip=true`, e.g. `-pl dolphinscheduler-common`.

### Frontend (dolphinscheduler-ui)

- Uses **pnpm lockfile** but `pnpm` v10+ blocks build scripts for `esbuild`. Use `npm install --legacy-peer-deps` instead to ensure `esbuild` postinstall runs correctly.
- **Dev server**: `npx vite --host 0.0.0.0` (port 5173). Proxies API calls to `http://127.0.0.1:12345`.
- **Lint**: `npx eslint src --ext .ts,.tsx,.vue`.
- **Format**: `npx prettier --write "src/**/*.{vue,ts,tsx}"`.

### Running the Standalone Server

The standalone server (all-in-one: API + Master + Worker + Alert) uses H2 in-memory DB and JDBC registry — no external dependencies required.

**Classpath setup** (must include `provided`-scope deps since they are excluded by default):

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
./mvnw -pl dolphinscheduler-standalone-server dependency:build-classpath \
  -Dspotless.skip=true -DincludeScope=provided -Dmdep.outputFile=/tmp/ds-cp.txt

CP="dolphinscheduler-standalone-server/target/classes:\
dolphinscheduler-standalone-server/src/main/resources:\
dolphinscheduler-api/src/main/resources:\
dolphinscheduler-dao/src/main/resources:\
$(cat /tmp/ds-cp.txt | tr ':' '\n' | grep -v slf4j-simple | tr '\n' ':')"

java -server -Xms1g -Xmx1g \
  -Dio.netty.transport.noNative=true \
  -cp "$CP" org.apache.dolphinscheduler.StandaloneServer \
  --alert.port=50060
```

**Key gotchas**:
- **Exclude `slf4j-simple`** from classpath — it conflicts with logback (Spring Boot needs `LogbackLoggingSystem`).
- **Port 50052** (default alert RPC port) may be occupied in Cloud Agent VMs. Use `--alert.port=50060` (or another free port) as a Spring Boot CLI argument.
- **`-Dio.netty.transport.noNative=true`** forces NIO over Epoll; Epoll may fail in sandboxed container environments.
- Resources `dynamic-task-type-config.yaml` (from `dolphinscheduler-api`) and `sql/dolphinscheduler_h2.sql` (from `dolphinscheduler-dao`) must be on the classpath.
- Logs go to `./logs/dolphinscheduler-standalone.log` (not stdout).

### Default credentials

- **Username**: `admin`
- **Password**: `dolphinscheduler123`
- UI at `http://localhost:5173`, API at `http://localhost:12345/dolphinscheduler/`
- Swagger: `http://localhost:12345/dolphinscheduler/swagger-ui/index.html`
- Health: `http://localhost:12345/dolphinscheduler/actuator/health`

### Reference

- Development setup guide: `docs/docs/en/contribute/development-environment-setup.md`
- Build instructions: `CONTRIBUTING.md`
