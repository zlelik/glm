# Contributing & Build Guide

Developer documentation for building, running and maintaining GLM. For the product overview see
[README.md](README.md).

## Building

The whole application (Java backend + React frontend) is built with a single Maven command. The React side
is built with [Vite](https://vitejs.dev/). The frontend build runs under `target/` (node, `node_modules`
and the bundle all live in `target/`), so `mvn clean` wipes them and the project root stays clean. The
React bundle is always written to `target/classes/static/built/bundle.js`.

| # | Goal | Command |
|---|------|---------|
| 1 | Production WAR, everything, **minified** JS | `mvn clean package` |
| 2 | Dev WAR (for Tomcat), everything, **unminified** JS | `mvn clean package -Pdev` |
| 3 | Build + run locally (embedded Tomcat), **unminified** JS | `mvn clean spring-boot:run -Pdev` |
| 4 | Rebuild **only React**, swap into an existing WAR (Java untouched) | `npm run build` + `jar uf` |
| 5 | Rebuild **only Java** (reuse minified React) | `mvn package -DskipFrontend=true` |

**(1) Production WAR — everything, minified.** The normal build: compiles the Java, runs the production
Vite build (minified React *production* bundle), runs the tests, and packages `target/ROOT.war`. Deploy it
as described in the Docker / Tomcat section of the README (drop `ROOT.war` into Tomcat's `webapps` folder).

```
mvn clean package
```

**(2) Deployable WAR with unminified JS** — same as case 1 but the React bundle is unminified (with source
maps), for debugging in a Tomcat/production-like environment. The `-Pdev` profile switches the Vite build
to development mode (and activates the Spring `dev` profile — see *Logging* below):

```
mvn clean package -Pdev
```

**(3) Build and run locally on the embedded Tomcat (no external Tomcat), unminified JS.** Plain
`mvn clean spring-boot:run` (without `-Pdev`) builds the **minified** bundle; add `-Pdev` for the unminified
one. For a live frontend loop, run `npm run watch` in a second terminal (unminified, rebuilds on every
change; needs a one-time `npm install`).

```
mvn clean spring-boot:run -Pdev
```

**(4) Rebuild only React, keep the Java immutable.** Use this when the Java is a stable, already-released
artifact and only the frontend changed — recompiling the Java would produce a different binary (different
hash) for no reason. The React production build produces a single file,
`target/classes/static/built/bundle.js` (everything is bundled into it). Build it directly, then replace
`bundle.js` inside the existing `ROOT.war`:

```
npm install        # once (creates a local node_modules in the project root, dev use only)
npm run build      # minified bundle -> target/classes/static/built/bundle.js
```

**(5) Rebuild only Java, reuse the existing React bundle.** Useful when only backend code changed. Run
**without** `clean` so the already-built bundle is kept; this skips the (slow) node install + Vite build:

```
mvn package -DskipFrontend=true
```

## Running locally

Use `mvn clean spring-boot:run` to run via Maven (add `-Pdev` for the unminified bundle + verbose logging).

On Windows, `run-app.bat build` builds and runs; `run-app.bat` runs when the build was already done.

## Running the tests

```
mvn test
```

JUnit 5 unit tests (game logic, utilities), a MockMvc API error-handling test, and Selenide end-to-end
tests (which need a Chrome/Chromium binary available). They also run as part of `mvn package`.

## Configuration & profiles

Game settings are bound to the typed `GameProperties` class (`gml.*` keys, kebab-case). Spring profiles
select the environment:

| Profile | How to activate | Database | Demo seeding | SQL / debug logs |
|---------|-----------------|----------|--------------|------------------|
| _(none)_ | default — plain run, and tests | in-memory H2 | yes | off |
| `dev` | `mvn spring-boot:run -Pdev` (sets `spring-boot.run.profiles=dev`) | in-memory H2 | yes | SQL + `info.gamed.glm` DEBUG on |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | MySQL (env vars) | **no** (`DatabaseLoader` is `@Profile("!prod")`) | off |

Per-profile settings live in `application-dev.properties` / `application-prod.properties`. The production
MySQL connection is externalized — set these environment variables when running with the `prod` profile:

```
SPRING_PROFILES_ACTIVE=prod
GLM_DB_URL=jdbc:mysql://<host>:3306/<database>
GLM_DB_USERNAME=<user>
GLM_DB_PASSWORD=<password>
```

`prod` uses `ddl-auto=update`: against a fresh MySQL it creates the tables, and against an existing one it
only adds missing tables/columns (never drops/renames/retypes). When the schema later needs renames or type
changes, switch to a migration tool (Flyway/Liquibase) with `validate`.

## Logging

Backend logging is configured in `src/main/resources/logback-spring.xml`. Verbose game-logic diagnostics
are logged at `DEBUG` and are only shown under the Spring `dev` profile. The Maven `dev` profile activates
it (`spring-boot.run.profiles=dev`), so `mvn spring-boot:run -Pdev` shows the per-tick logs while a normal
production run (no profile) stays at `INFO`.

Frontend logging uses [`loglevel`](https://github.com/pimterry/loglevel) via `src/main/js/logger.ts`. The
level is `debug` in development and `warn` in production (`import.meta.env.DEV`). Log a timestamp by passing
`ts()` as the first argument — `log.debug(ts(), 'message', obj)` — which keeps the clickable call-site line
number in DevTools.

## Git strategy

Standard master/develop model:

- <https://nvie.com/posts/a-successful-git-branching-model/>
- <https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow>

## Updating all dependencies to the latest versions

1. Check the latest version of the `spring-boot-starter-parent` artifact and update it if needed in
   `pom.xml` inside the `<parent>` tag.
2. Check the latest stable (LTS) version of Node.js: <https://nodejs.org/en/download>.
3. Update `node.version` and `npm.version` in `pom.xml` to those values:
   ```xml
   <node.version>v24.16.0</node.version>
   <npm.version>11.13.2</npm.version>
   ```
4. Get the latest stable version of frontend-maven-plugin:
   <https://github.com/eirslett/frontend-maven-plugin/tags>.
5. Update `frontend-maven-plugin.version` in `pom.xml` to that value.
6. In `target/node`, run `npm install npm-check-updates`.
7. Copy `package.json` from the project root into the `target/node` folder.
8. Run `./node_modules/.bin/ncu --format group` from `target/node`. It lists the modules to update,
   grouped by minor / major changes.
9. Update the versions in the root `package.json` (not the one inside `target/node`) manually.
