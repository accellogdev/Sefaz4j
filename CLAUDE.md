# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Sefaz4j is a pure-Java library (no framework, no `main`/`spring-boot:run`) that builds, signs,
XSD-validates, and transmits Brazilian NFe (electronic invoices) 4.00 to SEFAZ web services over
mutual-TLS SOAP. It targets the A1 (software-certificate) emission flow.

## Build & test

```bash
mvn compile                              # compile (also runs JAXB codegen, see below)
mvn test                                  # run the unit/integration-lite suite (JUnit 4)
mvn test -Dtest=ClassName#methodName      # run a single test method
mvn test -Pintegration-tests               # also run HomologacaoIntegrationTest (see below)
```

Java 25. There is no Spring Boot plugin and no packaged executable — `mvn package` just produces a
plain library jar.

### The `HomologacaoIntegrationTest`

`src/test/java/.../nfe/integration/HomologacaoIntegrationTest.java` calls the real SEFAZ Homologação
environment end-to-end. It is excluded by the default `maven-surefire-plugin` config (`**/integration/**`)
and only re-included by the `integration-tests` Maven profile. Even then, it self-skips
(`org.junit.Assume`) unless the environment variables `SEFAZ4J_TEST_PFX_PATH` and
`SEFAZ4J_TEST_PFX_SENHA` point at a real A1 certificate. `mvn test` alone never hits the network.

## Public API

Everything a consumer needs lives in the top-level package `net.accellog.sefaz4j.nfe`:

- **`Sefaz4jNFe`** — the facade. `emitir(Sefaz4jConfig, TNFe)` builds the chave de acesso, signs, XSD-validates,
  transmits, and (if the lot is still processing) polls `NFeRetAutorizacao4`, returning a `ResultadoEmissao`.
  `enviarXmlAssinado(Sefaz4jConfig, String)` skips straight to validate+transmit for an already-signed XML.
- **`Sefaz4jConfig`** — UF, `Ambiente`, PFX bytes + password, optional URL overrides
  (`urlAutorizacaoOverride` via constructor, `setUrlRetAutorizacaoOverride` fluently — mainly for tests/proxies),
  and fluent setters for `timeout`/`maxTentativasPolling`/`intervaloPolling` (defaults: 30s, 5, 5s).
- **`ResultadoEmissao`** — `isOk()` is `true` **only** when SEFAZ's returned `cStat` is `100`; `getXmlAutorizado()`
  is a single well-formed XML document (wrapped in `<nfeProc>` when a protocol/`protNFe` is present).
- **`Ambiente`** — `PRODUCAO`/`HOMOLOGACAO`, carries the numeric `tpAmb`.

### Error contract

`ok=false` in `ResultadoEmissao` means *only* "SEFAZ returned a non-100 `cStat`" (business rejection or
an unresolved still-processing state is never silently turned into `ok=false`). Every technical failure
propagates as one of three unchecked exception types instead:

- `net.accellog.sefaz4j.nfe.assinatura.CertificadoException` — bad PFX/password, or the signing step itself failed.
- `net.accellog.sefaz4j.nfe.validacao.ValidacaoXsdException` — the built/given XML fails XSD validation (carries `getViolacoes()`).
- `net.accellog.sefaz4j.nfe.webservice.ComunicacaoException` — HTTP/SOAP failure, unparseable SEFAZ response, or
  the lot still being `103` after `ReciboPoller` exhausts its polling attempts (an ambiguous outcome, deliberately
  not reported as `ok=false` — the caller must not blindly retry an NFe of unknown status).

## Package layout

- `net.accellog.sefaz4j.nfe` — the facade and config/result types described above.
- `chave` — `ChaveAcessoCalculator`: builds the 44-digit chave de acesso (43 digits + check digit, mod-11).
- `xml` — `NFeXmlBuilder`: marshals a `TNFe` (JAXB) into a DOM `Document`, injecting `infNFe/@Id` and `ide/cDV`
  from the computed chave when absent.
- `assinatura` — `AssinadorXml` (Apache Santuario XML signature), `CertificadoA1` (PKCS12 loading),
  `CertificadoException`.
- `validacao` — `ValidadorXsd`: validates a serialized XML string against the bundled `nfe_v4.00.xsd`
  chain; `ValidacaoXsdException`.
- `webservice` — `SefazHttpClient` (mutual-TLS `java.net.http.HttpClient`, with per-certificate
  `SSLContext`/`HttpClient` caching), `SoapEnvelopeBuilder`, `RespostaSefazParser`, `RespostaSefaz`,
  `ReciboPoller` (polls `NFeRetAutorizacao4` while `cStat == 103`), `ComunicacaoException`.
- `endpoints` — `EndpointResolver` + `UF`/`Ambiente`/`Servico`, backed by `src/main/resources/endpoints/nfe-servicos.ini`.
- `model` — **generated** JAXB classes (`TNFe`, `ObjectFactory`, etc.) — do not hand-edit, see below.

## Key technical facts for future sessions

- **JAXB codegen**: `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin` generates `net.accellog.sefaz4j.nfe.model`
  from the XSDs under `src/main/resources/schemas/nfe/` (`nfe_v4.00.xsd`, `enviNFe_v4.00.xsd`,
  `retEnviNFe_v4.00.xsd`, `consReciNFe_v4.00.xsd`, `retConsReciNFe_v4.00.xsd`) during `generate-sources`.
  The generated classes use the **`javax.xml.bind`** namespace (JAXB 2.3-era `jaxb-api` + `jaxb-runtime`),
  **not** `jakarta.xml.bind` — don't mix the two on the classpath. Sources land under
  `target/generated-sources/xjc/`; run `mvn generate-sources` (or `compile`) to (re)populate them before
  browsing `model` classes in an IDE.
- **Signing algorithm**: NFe signatures use **RSA-SHA1** (`SignatureMethod`) with a **SHA-1** digest and
  the plain (non-`WithComments`) C14N transform. This is fixed by the bundled, binding
  `schemas/nfe/xmldsig-core-schema_v1.01.xsd` (`<xsd:restriction>`, not a default) — SEFAZ mandates it for
  the DFe signature itself. This is unrelated to TLS (mutual-TLS to SEFAZ negotiates its own, unpinned,
  modern TLS version).
- **Bundled XSDs are trusted**; only `DocumentBuilderFactory` instances that parse *remote* SEFAZ response
  XML (`RespostaSefazParser`, the protocol-fragment parsing in `Sefaz4jNFe`) are hardened against
  XXE/DOCTYPE — `ValidadorXsd`'s `SchemaFactory` loading the bundled schema chain is intentionally left as-is.
- Maven resource filtering (`${...}` substitution) is scoped to exclude `schemas/**` and `endpoints/**` in
  `pom.xml`, so a literal `${...}` in a bundled XSD/ini is never mistaken for a Maven property placeholder.
- The `org.jvnet.jaxb2.maven2:maven-jaxb2-plugin:0.14.0` used for JAXB codegen bundles an old JAXB RI that
  calls `sun.misc.Unsafe.defineClass`, which was removed in JDK 24+; a clean `generate-sources` on JDK 25
  fails with `NoSuchMethodException`/`NoSuchFieldException` on `Injector` without the version overrides
  already pinned in the plugin's `<dependencies>` in `pom.xml` (`org.glassfish.jaxb:jaxb-xjc:2.3.5`,
  `org.glassfish.jaxb:jaxb-runtime:2.3.9`, `com.sun.xml.bind.external:rngom:4.0.5` — the last one because
  `jaxb-xjc:2.3.5` expects the old `com.sun.tools.rngom.*` package, but the `rngom` version it resolves by
  default ships the renamed `org.kohsuke.rngom.*` instead). Don't remove or downgrade these without
  re-verifying a full clean `mvn package` (delete `target/generated-sources` first — a stale generated
  model masks the failure).

## Release / CI

`.github/workflows/maven-publish.yml` builds, tests, and publishes the jar to **GitHub Packages**
(`https://maven.pkg.github.com/accellogdev/Sefaz4j`, `distributionManagement` in `pom.xml`) on
`release: created`. Two things that bite:

- The `release` event pins the commit the tag pointed to **at creation time**. Re-running a stale workflow
  run (or a run tied to an already-existing tag) rebuilds that old commit — it does not pick up new pushes
  to `main`. To actually test a `pom.xml`/workflow fix, cut a new release/tag against the current commit
  (bump the version) rather than just re-running an old job.
- GitHub Packages' Maven registry 422s on artifact IDs with uppercase letters — `artifactId` is `sefaz4j`
  (lowercase) for this reason, distinct from the project `<name>` (`Accellog`). Consumers also need a
  GitHub PAT with `read:packages` even for this public repo (see README for the `settings.xml` snippet) —
  GitHub Packages has no anonymous download for Maven.
- Current version follows a Maven-native pre-release qualifier scheme (`1.0.0-alpha-1`, `-alpha-2`, ...)
  so it sorts correctly before the eventual `1.0.0` release per Maven's version comparator.
