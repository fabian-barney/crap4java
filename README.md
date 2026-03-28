# crap4java

`crap4java` is a shared CRAP metric toolkit for Java projects.

It combines method cyclomatic complexity with JaCoCo method coverage and reports CRAP scores.
The current implementation still behaves like the original Maven-oriented CLI while the native Gradle and Maven plugin integrations are being built in dedicated modules.

## Modules

- `core`: analysis engine, CLI orchestration, and current Maven-oriented coverage runner
- `cli`: executable entrypoint that packages the core as a runnable jar
- `gradle-plugin`: placeholder module for the upcoming Gradle integration
- `maven-plugin`: placeholder module for the upcoming Maven integration

## Formula

`CRAP = CC^2 * (1 - coverage)^3 + CC`

- `CC` is cyclomatic complexity.
- `coverage` is method coverage fraction from JaCoCo `INSTRUCTION` counters.

## Coverage Pipeline

For each invocation today:

1. Delete stale coverage artifacts:
   - `target/site/jacoco/`
   - `target/jacoco.exec`
2. Run `mvn -q org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`
3. Read `target/site/jacoco/jacoco.xml`
4. Analyze selected Java files

## Build and Test

```bash
mvn test
```

## Run

Build the CLI jar:

```bash
mvn -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar
```

## CLI

```text
--help                Print usage to stdout
(no args)             Analyze all Java files under src/
--changed             Analyze changed Java files under src/
<file ...>            Analyze only these files
<directory ...>       Analyze all Java files under each directory's src/ subtree
```

Examples:

```bash
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar --help
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar --changed
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar src/main/java/demo/Sample.java
java -jar cli/target/crap4java-cli-0.1.0-SNAPSHOT.jar module-a module-b
```

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` CRAP threshold exceeded (`> 8.0`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
