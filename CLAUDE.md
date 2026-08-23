# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Sefaz4j is a pure-Java library (no framework, no `main`/`spring-boot:run`) that builds, signs,
XSD-validates, and transmits Brazilian NFe (electronic invoices) 4.00 to SEFAZ web services over
mutual-TLS SOAP. It targets the A1 (software-certificate) emission flow. CTe (Conhecimento de
Transporte Eletrônico) 4.00 emission was added alongside NFe as the library's second document type,
later joined by its post-emission lifecycle (see "## CTe (emissão + ciclo de vida pós-emissão)" below);
the two share the same root infrastructure packages.

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

Everything an NFe consumer needs lives in the top-level package `net.accellog.sefaz4j.nfe` (the CTe
equivalent, `net.accellog.sefaz4j.cte`, is documented in its own section below):

- **`Sefaz4jNFe`** — the facade. `emitir(Sefaz4jConfig, TNFe)` builds the chave de acesso, signs, XSD-validates,
  transmits, and (if the lot is still processing) polls `NFeRetAutorizacao4`, returning a `ResultadoEmissao`.
  `enviarXmlAssinado(Sefaz4jConfig, String)` skips straight to validate+transmit for an already-signed XML.
  `consultarSituacao(Sefaz4jConfig, String chaveAcesso)` consulta o status de uma NFe já
  transmitida, sem assinar nada. `cancelar(Sefaz4jConfig, String chaveAcesso, String nProt, String justificativa)`
  e `corrigirCartaDeCorrecao(Sefaz4jConfig, String chaveAcesso, String textoCorrecao[, int nSeqEvento])`
  montam e assinam um evento (`infEvento`, não `infNFe`) e o enviam a `RecepcaoEvento4`.
  `inutilizar(Sefaz4jConfig, String cUF, String ano, String cnpj, String serie, String nNFIni, String nNFFin, String justificativa)`
  inutiliza uma faixa de numeração não usada — não depende de uma NFe específica.
- **`Sefaz4jConfig`** — UF, `Ambiente`, PFX bytes + password, optional URL overrides
  (`urlAutorizacaoOverride` via constructor, `setUrlRetAutorizacaoOverride` fluently — mainly for tests/proxies),
  and fluent setters for `timeout`/`maxTentativasPolling`/`intervaloPolling` (defaults: 30s, 5, 5s).
- **`ResultadoEmissao`** — `isOk()` is `true` **only** when SEFAZ's returned `cStat` is `100`; `getXmlAutorizado()`
  is a single well-formed XML document (wrapped in `<nfeProc>` when a protocol/`protNFe` is present).
- **`ResultadoConsulta`/`ResultadoEvento`/`ResultadoInutilizacao`** — mesmo formato de `ResultadoEmissao`
  (`ok` estrito para o `cStat` de sucesso específico da operação — 100/135/102 respectivamente —, `cStat`/
  `xMotivo` sempre preenchidos para o chamador inspecionar qualquer outro código).
- **`Ambiente`** — `PRODUCAO`/`HOMOLOGACAO`, carries the numeric `tpAmb`.

### Error contract

`ok=false` in `ResultadoEmissao` means *only* "SEFAZ returned a non-100 `cStat`" (business rejection or
an unresolved still-processing state is never silently turned into `ok=false`). Every technical failure
propagates as one of three unchecked exception types instead:

- `net.accellog.sefaz4j.assinatura.CertificadoException` — bad PFX/password, or the signing step itself failed.
- `net.accellog.sefaz4j.validacao.ValidacaoXsdException` — the built/given XML fails XSD validation (carries `getViolacoes()`).
- `net.accellog.sefaz4j.webservice.ComunicacaoException` — HTTP/SOAP failure, unparseable SEFAZ response, or
  the lot still being `103` after `ReciboPoller` exhausts its polling attempts (an ambiguous outcome, deliberately
  not reported as `ok=false` — the caller must not blindly retry an NFe of unknown status).

## CTe (emissão + ciclo de vida pós-emissão)

`net.accellog.sefaz4j.cte` is the second document type, mirroring the NFe package shape. Fase 1 covered
only emission; fase 2 added the post-emission lifecycle (`consultarSituacao`/`cancelar`/
`corrigirCartaDeCorrecao`). **Inutilização is permanently out of scope for CTe** — SEFAZ discontinued it
for CTe 4.00 (confirmed against the ACBr reference implementation: `ACBrCTeWebServices.pas` raises an
exception for any v4.00 inutilização call, and no `CTeInutilizacao_4.00` key exists in any UF section of
`ACBrCTeServicos.ini`), so there is no `Sefaz4jCTe.inutilizar` and none is planned.

- **`Sefaz4jCTe`** — the facade. `emitir(Sefaz4jConfig, TCTe)` first checks
  that `infCte/ide/tpAmb` (when present) matches the configured `Ambiente`'s `tpAmb`, throwing
  `IllegalArgumentException` early otherwise (the same tpAmb-vs-Ambiente safety guard `Sefaz4jNFe.emitir`
  has); it then builds the chave de acesso + `Document` via `CTeXmlBuilder`, signs the `infCte` element,
  XSD-validates against `cte_v4.00.xsd`, and transmits once — there is **no lot/polling step**: CTe 4.00
  has no `enviCTe_v4.00.xsd` (only v2.00/v3.00 have one), the root element sent to `CTeRecepcaoSinc` is
  the `<CTe>` itself, and the `retCTe` response's `cStat`/`protCTe` are already final. `Sefaz4jCTe` has
  no `ReciboPoller` equivalent for this reason. `enviarXmlAssinado(Sefaz4jConfig, String)` skips straight
  to validate+transmit for an already-signed XML. `consultarSituacao(Sefaz4jConfig, String chaveAcesso)`
  consulta o status de um CT-e já transmitido, sem assinar nada, retornando `ResultadoConsulta`.
  `cancelar(Sefaz4jConfig, String chaveAcesso, String nProt, String justificativa)` (tpEvento `110111`,
  `nProt` com 15 dígitos, `justificativa` com 15 a 255 caracteres) e
  `corrigirCartaDeCorrecao(Sefaz4jConfig, String chaveAcesso, List<InfCorrecao> correcoes[, int
  nSeqEvento])` (tpEvento `110110`) montam e assinam um evento (`infEvento`, não `infCte`) e o enviam a
  `RecepcaoEvento_4.00`, retornando `ResultadoEvento`.
- **CC-e do CTe usa uma lista estruturada, não texto livre.** Ao contrário do
  `corrigirCartaDeCorrecao` da NFe (que recebe uma `String textoCorrecao`), o do CTe recebe
  `List<InfCorrecao>`: o XSD `evCCeCTe_v4.00.xsd` não tem campo de texto livre, só o grupo repetível
  `infCorrecao` (`grupoAlterado`/`campoAlterado`/`valorAlterado`, mais o `nroItemAlterado` opcional).
  `InfCorrecao` é um DTO imutável com construtores de 3 e 4 argumentos — o de 3 deixa
  `getNroItemAlterado()` como `null`.
- **Validação de evento em duas passadas.** Ao contrário da NFe (que tem um schema "leiaute" próprio por
  operação, `leiauteEventoCancNFe_v1.00.xsd`/`leiauteCCe_v1.00.xsd`), o CTe não tem um schema
  per-operation dedicado — então cada evento é validado duas vezes: o fragmento específico
  (`evCancCTe_v4.00.xsd`/`evCCeCTe_v4.00.xsd`) antes de embrulhá-lo em `detEvento`, e o documento
  `eventoCTe` completo e assinado contra o schema genérico `eventoCTe_v4.00.xsd` logo antes de
  transmitir.
- **`net.accellog.sefaz4j.cte.Sefaz4jConfig`** — `UF`, `Ambiente`, PFX bytes + password, optional
  `urlAutorizacaoOverride` (constructor arg, or fluent `setUrlAutorizacaoOverride`), fluent
  `setUrlConsultaProtocoloOverride`/`setUrlRecepcaoEventoOverride` for the fase-2 endpoints, and a
  fluent `setTimeout` (default 30s). No polling-related setters — there is nothing to poll.
- **`net.accellog.sefaz4j.cte.ResultadoEmissao`** — same shape as the NFe one: `isOk()` is `true` only
  when `cStat` is `100`; `getCStat()`/`getXMotivo()` always populated; `getChCTe()` (chave de acesso);
  `getXmlAutorizado()` is a single well-formed XML document, wrapped in `<cteProc>` when a `protCTe` is
  present.
- **`net.accellog.sefaz4j.cte.ResultadoConsulta`/`ResultadoEvento`** — mesmo formato de
  `ResultadoEmissao`: `isOk()` estrito para o `cStat` de sucesso específico da operação (`100` para
  consulta, `135` para cancelamento/CC-e), `getCStat()`/`getXMotivo()` sempre preenchidos.
  `ResultadoConsulta` tem `getChCTe()` (em vez do `getChNFe()` da NFe) e `getProtocoloXml()`;
  `ResultadoEvento` tem `getNProt()`/`getProtocoloXml()`, igual à NFe.
- **`infModal` is `xs:any` — deliberately untyped/unvalidated.** `cteTiposBasico_v4.00.xsd` declares
  `infCTeNorm/infModal` as an `<xs:any processContents="skip"/>`, so the official layout itself delegates
  modal-specific content (rodoviário/aéreo/aquaviário/ferroviário/dutoviário, each with its own XSD) to
  an unvalidated extension point — this isn't a gap introduced by this library. `CTeXmlBuilder` and
  `ValidadorXsd` never generate or validate that fragment; the caller builds the modal-specific DOM
  content themselves and sets it on the `TCTe` before calling `emitir`. None of the `cteModal*.xsd`
  files are bundled or referenced anywhere in the library.

## Package layout

- `net.accellog.sefaz4j.nfe` / `net.accellog.sefaz4j.cte` — the two document-specific facades and
  their config/result types, described above. The shared infrastructure packages below
  (`chave`, `assinatura`, `validacao`, `webservice`, `endpoints`) live at the root and are used by both.
- `net.accellog.sefaz4j.chave` (shared) — `ChaveAcessoCalculator`: builds the 44-digit chave de acesso (43 digits + check digit, mod-11).
- `net.accellog.sefaz4j.nfe.xml` — `NFeXmlBuilder`: marshals a `TNFe` (JAXB) into a DOM `Document`, injecting `infNFe/@Id` and `ide/cDV`
  from the computed chave when absent.
- `net.accellog.sefaz4j.cte.xml` — `CTeXmlBuilder`: same role for `TCTe`, injecting `infCte/@Id` and `ide/cDV`.
- `net.accellog.sefaz4j.assinatura` (shared) — `AssinadorXml` (Apache Santuario XML signature), `CertificadoA1` (PKCS12 loading),
  `CertificadoException`.
- `net.accellog.sefaz4j.validacao` (shared) — `ValidadorXsd`: validates a serialized XML string against the bundled `nfe_v4.00.xsd`/
  `cte_v4.00.xsd` chains (root XSD path is a parameter); `ValidacaoXsdException`.
- `net.accellog.sefaz4j.webservice` (shared) — `SefazHttpClient` (mutual-TLS `java.net.http.HttpClient`, with per-certificate
  `SSLContext`/`HttpClient` caching), `RespostaSefazParser`, `RespostaSefaz`, `ComunicacaoException`.
- `net.accellog.sefaz4j.nfe.webservice` — `SoapEnvelopeBuilder`, `ReciboPoller` (polls `NFeRetAutorizacao4` while `cStat == 103`);
  NFe-specific, not shared.
- `net.accellog.sefaz4j.cte.webservice` — `SoapEnvelopeBuilder` with `envelopeRecepcaoSinc(String)`/
  `envelopeConsultaSituacao(String)`/`envelopeRecepcaoEvento(String)`; CTe-specific, not shared
  (no `ReciboPoller` equivalent — see the CTe section above).
- `net.accellog.sefaz4j.endpoints` (shared) — `EndpointResolver` + `UF`/`Ambiente`, backed by `src/main/resources/endpoints/nfe-servicos.ini`
  (NFe) and `cte-servicos.ini` (CTe), selected via a path/section-prefix argument to `EndpointResolver.resolver(...)`.
- `net.accellog.sefaz4j.nfe.endpoints` — `Servico`; NFe-specific, not shared.
- `net.accellog.sefaz4j.cte.endpoints` — `Servico` (`CTE_RECEPCAO_SINC`/`CTE_CONSULTA_PROTOCOLO`/
  `CTE_RECEPCAO_EVENTO`); CTe-specific, not shared.
- `net.accellog.sefaz4j.nfe.model` — **generated** JAXB classes (`TNFe`, `ObjectFactory`, etc.) — do not hand-edit, see below.
- `net.accellog.sefaz4j.cte.model` — **generated** JAXB classes (`TCTe`, `ObjectFactory`, etc.), from `cte_v4.00.xsd` — do not hand-edit.

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
- **Consulta/evento/inutilização não usam JAXB.** Os XSDs oficiais de evento
  (`leiauteEventoCancNFe_v1.00.xsd`, `leiauteCCe_v1.00.xsd`) e de consulta (`leiauteConsSitNFe_v4.00.xsd`)
  cada um redefine seu próprio `TEvento`/`TRetEvento`/`TProcEvento` no mesmo target namespace — incluir
  mais de uma dessas cadeias na mesma execução do `maven-jaxb2-plugin` quebra o XJC com tipo duplicado.
  Por isso `consultarSituacao`/`cancelar`/`corrigirCartaDeCorrecao`/`inutilizar` constroem e leem XML à
  mão (mesmo estilo de `SoapEnvelopeBuilder`/`RespostaSefazParser`), nunca via JAXB — `AssinadorXml` e
  `ValidadorXsd` foram generalizados (nome do elemento a assinar / caminho do XSD raiz como parâmetro)
  em vez de duplicados. Os XSDs correspondentes em `src/main/resources/schemas/nfe/` são só para
  `ValidadorXsd`, nunca aparecem no `schemaIncludes` do plugin JAXB.
- **`src/main/resources/schemas/nfe/leiauteCCe_v1.00.xsd` difere deliberadamente do arquivo oficial
  da SEFAZ**: o arquivo oficial redefine o `xs:complexType`/`xs:simpleType` `TCOrgaoIBGE`, que já
  vem incluído de `tiposBasico_v1.03.xsd` — um defeito de autoria genuíno do arquivo oficial que o
  `SchemaFactory`/validador XSD do Java rejeita com `sch-props-correct.2` (tipo duplicado no mesmo
  namespace). O bloco duplicado foi removido desta cópia bundled (comentário no próprio arquivo, logo
  após o `simpleType` `TVerEvento`); a versão de referência original, com o defeito, continua em
  `Schemas/NFe/leiauteCCe_v1.00.xsd`, fora do controle de versão — não copie esse arquivo de volta
  por cima do bundled.

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
