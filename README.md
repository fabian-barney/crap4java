# crap-java

CRAP metrics for Java.

It combines method cyclomatic complexity with JaCoCo method coverage and reports CRAP scores.
The toolkit resolves Maven and Gradle modules natively, including standard multi-module layouts, and publishes a standalone CLI plus dedicated Gradle and Maven plugins.

## Modules

- `core`: analysis engine, build-tool-neutral CLI orchestration, and Maven/Gradle coverage runner
- `cli`: executable entrypoint that bundles the core as a runnable jar
- `gradle-plugin`: self-contained Gradle plugin build exposing `media.barney.crap-java`
- `maven-plugin`: native Maven plugin exposing the `check` goal

## Formula

`CRAP = CC^2 * (1 - coverage)^3 + CC`

- `CC` is cyclomatic complexity.
- `coverage` is method coverage fraction from JaCoCo `INSTRUCTION` counters.

## Coverage Pipeline

For each resolved module today:

1. Detect Maven or Gradle automatically, unless `--build-tool` is supplied.
2. Delete stale JaCoCo artifacts for the detected build tool.
3. Run the module-scoped coverage command:
   - Maven: `mvn` or `mvnw`, using JaCoCo `0.8.13`
   - Gradle: `gradle` or `gradlew`, running `test` and `jacocoTestReport`
4. Read the module report:
   - Maven: `target/site/jacoco/jacoco.xml`
   - Gradle: `build/reports/jacoco/test/jacocoTestReport.xml`
5. Analyze the selected Java files for that module

## Build and Test

```bash
mvn -B -pl cli -am package
```

Build and test the Gradle plugin module after packaging the core jar:

```bash
mvn -B -pl core -am package
cd gradle-plugin
./gradlew test
```

Build and test the Maven plugin module, including its invoker integration fixtures:

```bash
mvn -B -pl maven-plugin -am verify
```

## Run

Build the CLI jar:

```bash
mvn -B -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/crap-java-cli-0.2.0.jar
```

## CLI

```text
--help                Print usage to stdout
(no args)             Analyze all Java files under any nested src/main/java tree
--changed             Analyze changed Java files under any nested src/main/java tree
--build-tool <tool>   Force `auto`, `maven`, or `gradle`
<file ...>            Analyze only these files
<directory ...>       Analyze all Java files under each directory's nested src/main/java trees
```

Examples:

```bash
java -jar cli/target/crap-java-cli-0.2.0.jar --help
java -jar cli/target/crap-java-cli-0.2.0.jar
java -jar cli/target/crap-java-cli-0.2.0.jar --changed
java -jar cli/target/crap-java-cli-0.2.0.jar --build-tool gradle
java -jar cli/target/crap-java-cli-0.2.0.jar --build-tool maven module-a/src/main/java/demo/Sample.java
java -jar cli/target/crap-java-cli-0.2.0.jar src/main/java/demo/Sample.java
java -jar cli/target/crap-java-cli-0.2.0.jar module-a module-b
```

## Distribution

Public releases are intended to ship through Maven Central and the Gradle Plugin Portal:

- `media.barney:crap-java-core:<version>`
- `media.barney:crap-java-cli:<version>`
- `media.barney:crap-java-maven-plugin:<version>`
- Gradle plugin id `media.barney.crap-java` version `<version>`

### Gradle Plugin Portal

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.crap-java") version "<version>"
}
```

No custom `pluginManagement` repository configuration is required for published releases.

Run:

```bash
./gradlew crap-java-check
```

### Maven Central

Add the plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.13</version>
      <executions>
        <execution>
          <goals>
            <goal>prepare-agent</goal>
          </goals>
        </execution>
        <execution>
          <id>report</id>
          <phase>verify</phase>
          <goals>
            <goal>report</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
    <plugin>
      <groupId>media.barney</groupId>
      <artifactId>crap-java-maven-plugin</artifactId>
      <version>&lt;version&gt;</version>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

The Maven plugin consumes the JaCoCo XML files produced by your build. It does not spawn a nested Maven run to generate coverage.

No custom `<pluginRepositories>` or consumer-side authentication are required for published releases.

Run:

```bash
mvn verify
```

## Release

Before the first public release:

1. Verify the `media.barney` namespace in the Sonatype Central Portal.
2. Generate a Central Portal user token.
3. Generate a Gradle Plugin Portal API key and secret.
4. Configure these CI secrets:
   - `MAVEN_CENTRAL_TOKEN_USERNAME`
   - `MAVEN_CENTRAL_TOKEN_PASSWORD`
   - `MAVEN_GPG_PRIVATE_KEY`
   - `MAVEN_GPG_PASSPHRASE`
   - `GRADLE_PUBLISH_KEY`
   - `GRADLE_PUBLISH_SECRET`

Local publishing preflight:

```bash
mvn -B -Prelease -Dcentral.skipPublishing=true -pl .,cli,maven-plugin -am deploy
```

```bash
cd gradle-plugin
./gradlew test validatePlugins
```

Release from `main` after the pull request checks are green:

1. Update the project version.
2. Tag `v<version>`.
3. Push the tag.

The tag-triggered release workflow publishes Maven artifacts to Maven Central, publishes the Gradle plugin to the Gradle Plugin Portal, and creates the repository release entry.

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` CRAP threshold exceeded (`> 8.0`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
