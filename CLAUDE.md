# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Sefaz4j is a pure-Java library (no framework, no `main`/`spring-boot:run`) that builds, signs,
XSD-validates, and transmits Brazilian NFe (electronic invoices) 4.00 to SEFAZ web services over
mutual-TLS SOAP. It targets the A1 (software-certificate) emission flow. CTe (Conhecimento de
Transporte Eletrônico) 4.00 emission was added alongside NFe as the library's second document type,
later joined by its post-emission lifecycle (see "## CTe (emissão + ciclo de vida pós-emissão)" below);
the two share the same root infrastructure packages. NFS-e (Padrão Nacional / SEFIN Nacional) is the
third document type (see "## NFS-e (Padrão Nacional)" below) and the architectural outlier: REST+JSON
transport instead of SOAP, and a single national endpoint instead of per-UF ones. MDFe (Manifesto
Eletrônico de Documentos Fiscais) 3.00 is the fourth document type (see "## MDFe (Padrão SOAP, 3.00) —
emissão + ciclo de vida" below): architecturally it swings back toward NFe rather than CTe or NFS-e —
SOAP over mutual TLS, per-UF/Ambiente endpoints, and NFe's lot+recibo emission flow (`MDFeRecepcao`/
`MDFeRetRecepcao` polling), not CTe's synchronous one.

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
equivalent, `net.accellog.sefaz4j.cte`, and the NFS-e equivalent, `net.accellog.sefaz4j.nfse`, are
documented in their own sections below):

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

## NFS-e (Padrão Nacional)

`net.accellog.sefaz4j.nfse` is the third document type, and architecturally the outlier of the three:
transport is **REST+JSON, not SOAP**, and there is **no UF/Ambiente-per-state split** — a single
national endpoint (ADN/SEFIN Nacional) serves every municipality, so
`net.accellog.sefaz4j.nfse.Sefaz4jConfig` takes no `UF` constructor argument (unlike the NFe/CTe
configs). The signed XML is gzip-compressed, Base64-encoded, and wrapped in a small JSON object for
both request and response (see `net.accellog.sefaz4j.nfse.webservice.PayloadCompactado`/
`RespostaNFSeParser`), mirroring the pattern in reverse for the response.

- **`Sefaz4jNFSe`** — the facade, with 5 methods:
  - `emitir(Sefaz4jConfig config, TCDPS dps): ResultadoEmissao` — injects `infDPS/@Id` (via
    `DpsIdCalculator`) if absent, signs `infDPS`, XSD-validates against `DPS_v1.01.xsd`, POSTs the
    compressed/encoded JSON body, and parses the response.
  - `enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado): ResultadoEmissao` — skips straight
    to validate+transmit for an already-signed DPS XML.
  - `consultarSituacao(Sefaz4jConfig config, String chaveAcesso): ResultadoConsulta` — GETs
    `{baseUrl}/{chaveAcesso}`.
  - `cancelar(Sefaz4jConfig config, String chaveAcesso, String cnpjAutor, int cMotivo, String xMotivo): ResultadoEvento`
    — builds+signs the `e101101` event and POSTs it to `{baseUrl}/{chaveAcesso}/eventos`.
  - `cancelarPorSubstituicao(Sefaz4jConfig config, String chaveAntiga, String cnpjAutor, TCDPS dpsSubstituta): ResultadoEvento`
    — issues the substitute DPS via `emitir`; only if that emission's own `ResultadoEmissao.isOk()` is
    `true` does it then build+sign the `e105102` event referencing both the old and new keys. If the
    substitute emission fails (technically or by business rejection), the event is never attempted and
    the returned `ResultadoEvento` carries the failed emission's `cStat`/`mensagem` instead.
- **The document Sefaz4j builds/signs is `net.accellog.sefaz4j.nfse.model.TCDPS`** (JAXB-generated
  from `DPS_v1.01.xsd`) — the DPS (Declaração de Prestação de Serviços), analogous to how NFe/CTe
  build `TNFe`/`TCTe`. The root XML element is `<DPS>` (`ObjectFactory.createDPS`), but its Java type
  is `TCDPS`, not a class literally named `DPS`.
- **Three distinct, non-interchangeable identifiers:**
  1. `infDPS/@Id` — the DPS's own Id, computed locally by `net.accellog.sefaz4j.nfse.chave.DpsIdCalculator`
     when absent: `"DPS" + cLocEmi(7) + tipoInscricaoFederal(1) + inscricaoFederal(14) + serie(5) +
     nDPS(15)`, pure concatenation with **no check digit** — a different algorithm from NFe/CTe's
     mod-11 `ChaveAcessoCalculator`. This is why `DpsIdCalculator` lives under `nfse.chave` rather than
     the shared root `chave` package.
  2. `chaveAcesso`/`chNFSe` — the NFS-e's own access key. Sefaz4j **never computes this one**: it's
     read from the `"chaveAcesso"` field of the server's JSON response to `emitir`/`enviarXmlAssinado`
     (exposed as `ResultadoEmissao.getChaveAcesso()`) and thereafter passed back in as an opaque
     `String` to `consultarSituacao`/`cancelar`/`cancelarPorSubstituicao` (only locally checked to be
     50 alphanumeric characters, never otherwise validated or parsed).
  3. `infNFSe/@Id` (`TSIdNFSe`) — an attribute embedded inside the *returned* NFS-e XML itself
     (present in `getXmlAutorizado()`'s raw text). Sefaz4j never parses it out or computes it; it's
     just along for the ride inside the XML.
- **Event structure is 4 levels deep, unlike NFe/CTe's 2.** NFe/CTe events nest `evento` →
  `infEvento` (signed) → an event-specific fragment. NFS-e nests `evento` → `infEvento` (signed,
  required) → `pedRegEvento` (embedded; also independently declared as its own root element in
  `pedRegEvento_v1.01.xsd`, enabling the same two-pass validation convention already used for CTe
  events) → `infPedReg` → the event-specific element (`e101101`/`e105102`). Two separately-computed
  `Id` attributes exist with **different formulas** — see
  `net.accellog.sefaz4j.nfse.xml.EventoNFSeXmlBuilder`'s class javadoc for the full derivation,
  including a documented XSD self-contradiction the implementer found and resolved (`TSIdPedRegEvt`'s
  doc comment says `"PRE"` + key + event type + `nPedRegEvento`, but its own `maxLength`/pattern only
  leave room for key + event type — no `nPedRegEvento` — so the pattern, which the validator actually
  enforces, wins over the comment).
- **`cnpjAutor` is an explicit parameter on `cancelar`/`cancelarPorSubstituicao`**, unlike NFe/CTe,
  which derive the equivalent CNPJ by slicing a fixed substring out of their own access key
  (`chaveAcesso.substring(6, 20)`). NFS-e's `chNFSe` has no internal byte layout confirmed in this
  library from which to safely extract a CNPJ, so the caller must supply it directly.
- **`cancelarPorSubstituicao`'s `cMotivo`/`xMotivo` are NOT separate parameters** — they come from
  `dpsSubstituta.getInfDPS().getSubst()` (a `TCSubstituicao`), because `TE105102`'s own XSD
  documentation states these fields are sourced from the DPS's `subst` group. The facade validates
  that `subst.getChSubstda()` equals `chaveAntiga` and throws `IllegalArgumentException` early if
  `subst` is null or mismatched.
- **No Carta de Correção equivalent exists in this standard** — `cancelarPorSubstituicao`
  (substituição) is the mechanism used to correct an already-issued NFS-e instead.
- **Confirmação/rejeição events (tomador/intermediário) are explicitly out of scope** for this plan
  (a separate "Plano B" scoped out at design time, not yet implemented) — don't assume they exist in
  this library.
- **Operational caveat:** `cancelarPorSubstituicao` has no atomicity between issuing the substitute
  DPS and submitting the cancellation event. If the substitute is issued successfully but the event
  submission then fails, the new NFS-e exists and the old one remains active — there is no dedicated
  "retry just the event" method.

### Unverified/unconfirmed protocol details (NFS-e)

Several aspects of the NFS-e (SEFIN Nacional/ADN) integration are **best-effort guesses, not verified
facts**, each flagged with an `ATENÇÃO` comment at its definition site in `Sefaz4jNFSe.java` (or, for
the last item, in `DpsXmlBuilder.java`). A future session must not treat these as settled:

- **Signing algorithm** — `ALGORITMO_ASSINATURA`/`ALGORITMO_DIGEST` are RSA-SHA256/SHA-256, chosen as
  "the most likely modern federal default"; never tested against a real Homologação endpoint.
- **Event JSON field name** — `CAMPO_JSON_EVENTO = "eventoXmlGZipB64"` is inferred purely from the
  naming convention already observed in `dpsXmlGZipB64`/`nfseXmlGZipB64`, not confirmed against the
  SEFIN Nacional integration manual.
- **Event submission endpoint path** — `{baseUrl}/{chaveAcesso}/eventos`, not confirmed.
- **Which document the ADN expects signed for an event submission** — the current code assumes the
  full `evento`, signed at its outer, required `infEvento` signature (mirroring NFe/CTe's convention
  of signing the outermost wrapper). The unruled-out alternative is that the ADN instead wants just
  the embedded `pedRegEvento` signed at its own optional (`minOccurs="0"`) `infPedReg` signature. If
  Homologação rejects the current choice, `EventoNFSeXmlBuilder` already separates
  `montarPedRegEvento`/`envolverEmEvento`, so the fix is isolated to signing/transmitting the former
  instead of the latter.
- **CNPJ/CPF-type-code mapping used when auto-generating `infDPS/@Id`**
  (`DpsXmlBuilder.injetarIdSeAusente`) — the numeric codes (CNPJ=2, CPF=1, NIF=3, cNaoNIF=9) and the
  left-zero-pad-to-14 convention are a best-effort placeholder, not confirmed against any official
  manual or the ACBr reference implementation.

## MDFe (Padrão SOAP, 3.00) — emissão + ciclo de vida

`net.accellog.sefaz4j.mdfe` is the fourth document type, and architecturally sits closer to NFe than
to CTe or NFS-e: SOAP over mutual TLS, per-UF/Ambiente endpoints (`mdfe-servicos.ini`), and — unlike
CTe's synchronous `CTeRecepcaoSinc` — a lot+recibo emission flow. `MDFeRecepcao` accepts the lot and
may answer `cStat 103` ("em processamento"), in which case `Sefaz4jMDFe.emitir` polls
`MDFeRetRecepcao` via `ReciboPoller`, exactly like `Sefaz4jNFe.emitir` does. This isn't a design
choice made by this library — MDFe 3.00's own WSDL splits synchronous and asynchronous submission
into two entirely separate services rather than exposing an `enviMDFe`-level `indSinc` switch, and
this library implements only the asynchronous pair (`MDFeRecepcao`/`MDFeRetRecepcao`), confirmed
against the ACBr reference implementation.

**No inutilização for MDFe, and for a stronger reason than CTe's.** CTe's inutilização was merely
*discontinued* for v4.00 (see the CTe section above); MDFe's inutilização **never existed in the
standard at all** — confirmed by its total absence anywhere in the ACBr reference implementation, not
just for one version. There is no `Sefaz4jMDFe.inutilizar` and none is planned.

- **`Sefaz4jMDFe`** — the facade, with 6 methods:
  - `emitir(Sefaz4jConfig config, TMDFe mdfe): ResultadoEmissao` — checks `infMDFe/ide/tpAmb` (when
    present) against the configured `Ambiente`, throwing `IllegalArgumentException` early on a
    mismatch (the same tpAmb-vs-Ambiente guard `Sefaz4jNFe`/`Sefaz4jCTe` already have); builds the
    chave de acesso + `Document` via `MDFeXmlBuilder`, signs `infMDFe`, XSD-validates against
    `mdfe_v3.00.xsd`, transmits to `MDFeRecepcao`, and polls `MDFeRetRecepcao` if the lot comes back
    `103`.
  - `enviarXmlAssinado(Sefaz4jConfig config, String xmlAssinado): ResultadoEmissao` — skips straight
    to validate+transmit(+poll) for an already-signed MDF-e XML.
  - `consultarSituacao(Sefaz4jConfig config, String chaveAcesso): ResultadoConsulta` — builds and
    sends a `consSitMDFe` (validated against `consSitMDFe_v3.00.xsd`) to `MDFeConsultaProtocolo`, no
    signing involved.
  - `cancelar(Sefaz4jConfig config, String chaveAcesso, String nProt, String justificativa):
    ResultadoEvento` — tpEvento `110111`; `nProt` must be exactly 15 numeric digits, `justificativa`
    between 15 and 255 characters (same limits as NFe/CTe cancelamento).
  - `encerrar(Sefaz4jConfig config, String chaveAcesso, String nProt, String cUFEnc, String cMunEnc,
    String dtEnc): ResultadoEvento` — tpEvento `110112` (encerramento); requires `nProt` (15 digits)
    plus the closing UF/município/date the manifest's travel actually ended at, since a manifest is
    routinely closed somewhere other than where `infMDFe/ide` originally planned.
  - `incluirCondutor(Sefaz4jConfig config, String chaveAcesso, String xNome, String cpf):
    ResultadoEvento` — tpEvento `110114`; unlike `cancelar`/`encerrar`, takes **no `nProt`** —
    `evIncCondutorMDFe`'s schema has no such field, because adding a driver mid-manifest isn't tied to
    the original authorization protocol the way cancelling or closing the manifest is. This 4-arg form
    delegates to a 5th-arg overload `incluirCondutor(..., int nSeqEvento)` with `nSeqEvento=1`, the
    same overload pattern `Sefaz4jCTe`/`Sefaz4jNFe`'s `corrigirCartaDeCorrecao` already use — needed
    because `evIncCondutorMDFe_v3.00.xsd` only allows one `condutor` per event, so a second driver on
    the same manifest requires a second event with `nSeqEvento=2`.
  - All three event methods build the event via the shared `EventoMDFeXmlBuilder.montar(...)`, sign
    `infEvento`, XSD-validate the event-specific fragment first (`evCancMDFe_v3.00.xsd`/
    `evEncMDFe_v3.00.xsd`/`evIncCondutorMDFe_v3.00.xsd`) and then the assembled `eventoMDFe_v3.00.xsd`
    document (the same two-pass validation convention already used for CTe/NFS-e events), then POST
    to `RecepcaoEvento_3.00` — returning a `ResultadoEvento` whose `isOk()` is strict for `cStat 135`.
- **Explicitly out of scope**, the same "Plano B" precedent already used for NFS-e's confirmação/
  rejeição events:
  - `MDFeConsultaMDFeNaoEnc` — an operational report ("which MDF-es for this CNPJ are still open"),
    not a per-document operation, so it doesn't fit this library's chaveAcesso-scoped method shape.
  - The four v3.00-only payment/GNRE events (`evConfirmaServMDFe`/`evPagtoOperMDFe`/
    `evAlteracaoPagtoServMDFe`/`evInclusaoDFeMDFe`) — a separate payment-confirmation workflow, not
    part of the core emissão/cancelamento/encerramento/condutor lifecycle this plan scoped.
  - Fleet/manifest-registration services (`mdfeManCadTransp`/`Frota`) — pre-registering vehicles/
    drivers with SEFAZ is a distinct concern from emitting and managing MDF-e documents themselves.
  - `distMDFe` (document distribution/download) — a pull-style query for documents addressed to a
    given CNPJ, not part of the emit-and-manage lifecycle this library covers.
- **`net.accellog.sefaz4j.mdfe.Sefaz4jConfig`** — `UF`, `Ambiente`, PFX bytes + password, optional
  `urlRecepcaoOverride` (constructor arg), fluent `setUrlRetRecepcaoOverride`/
  `setUrlConsultaProtocoloOverride`/`setUrlRecepcaoEventoOverride`, and fluent
  `setTimeout`/`setMaxTentativasPolling`/`setIntervaloPolling` (defaults: 30s, 5, 5s — same as NFe).
  The override method names use MDFe's own WSDL vocabulary — "Recepção"/"RetRecepção" — rather than
  NFe's "Autorização", because MDFe's actual services are never called `MDFeAutorizacao`.
- **`net.accellog.sefaz4j.mdfe.ResultadoEmissao`/`ResultadoConsulta`/`ResultadoEvento`** — same shape
  as NFe's: `isOk()` strict for the operation's specific success `cStat` (`100` for emissão/consulta,
  `135` for any of the three events), `getCStat()`/`getXMotivo()` always populated;
  `ResultadoEmissao`/`ResultadoConsulta` expose `getChMDFe()` (chave de acesso, MDFe's own naming, not
  `getChNFe()`); `getXmlAutorizado()` wraps in `<mdfeProc>` when a `protMDFe` is present.
- **Chave de acesso uses the shared `chave` package, unlike NFS-e.** `MDFeXmlBuilder` calls the same
  root `net.accellog.sefaz4j.chave.ChaveAcessoCalculator` (mod-11) NFe/CTe use — there is no
  `mdfe.chave` package — because MDFe's access key follows the same 44-digit DFe convention, unlike
  NFS-e's unrelated, check-digit-less `infDPS/@Id` scheme.

## Package layout

- `net.accellog.sefaz4j.nfe` / `net.accellog.sefaz4j.cte` / `net.accellog.sefaz4j.nfse` /
  `net.accellog.sefaz4j.mdfe` — the four document-specific facades and their config/result types,
  described above. The shared infrastructure packages below (`chave`, `assinatura`, `validacao`,
  `webservice`, `endpoints`) live at the root and are used by all four (NFS-e's `chave` and event-XML
  logic is the exception — see the NFS-e section above for why `DpsIdCalculator` lives under
  `nfse.chave` instead; MDFe follows NFe/CTe's convention and has no `chave` package of its own).
- `net.accellog.sefaz4j.chave` (shared) — `ChaveAcessoCalculator`: builds the 44-digit chave de acesso (43 digits + check digit, mod-11).
- `net.accellog.sefaz4j.nfe.xml` — `NFeXmlBuilder`: marshals a `TNFe` (JAXB) into a DOM `Document`, injecting `infNFe/@Id` and `ide/cDV`
  from the computed chave when absent.
- `net.accellog.sefaz4j.cte.xml` — `CTeXmlBuilder`: same role for `TCTe`, injecting `infCte/@Id` and `ide/cDV`.
- `net.accellog.sefaz4j.assinatura` (shared) — `AssinadorXml` (Apache Santuario XML signature), `CertificadoA1` (PKCS12 loading),
  `CertificadoException`.
- `net.accellog.sefaz4j.validacao` (shared) — `ValidadorXsd`: validates a serialized XML string against the bundled `nfe_v4.00.xsd`/
  `cte_v4.00.xsd`/`DPS_v1.01.xsd`/`evento_v1.01.xsd`/`pedRegEvento_v1.01.xsd`/`mdfe_v3.00.xsd`/
  `consSitMDFe_v3.00.xsd`/`evCancMDFe_v3.00.xsd`/`evEncMDFe_v3.00.xsd`/`evIncCondutorMDFe_v3.00.xsd`/
  `eventoMDFe_v3.00.xsd` chains (root XSD path is a parameter); `ValidacaoXsdException`. Also carries
  a static initializer raising `jdk.xml.maxOccurLimit` (see "Key technical facts" below) needed only
  by the MDFe schema chain.
- `net.accellog.sefaz4j.webservice` (shared) — `SefazHttpClient` (mutual-TLS `java.net.http.HttpClient`, with per-certificate
  `SSLContext`/`HttpClient` caching), `RespostaSefazParser`, `RespostaSefaz`, `ComunicacaoException`.
- `net.accellog.sefaz4j.nfe.webservice` — `SoapEnvelopeBuilder`, `ReciboPoller` (polls `NFeRetAutorizacao4` while `cStat == 103`);
  NFe-specific, not shared.
- `net.accellog.sefaz4j.cte.webservice` — `SoapEnvelopeBuilder` with `envelopeRecepcaoSinc(String)`/
  `envelopeConsultaSituacao(String)`/`envelopeRecepcaoEvento(String)`; CTe-specific, not shared
  (no `ReciboPoller` equivalent — see the CTe section above).
- `net.accellog.sefaz4j.endpoints` (shared) — `EndpointResolver` + `UF`/`Ambiente`, backed by `src/main/resources/endpoints/nfe-servicos.ini`
  (NFe), `cte-servicos.ini` (CTe), and `mdfe-servicos.ini` (MDFe), selected via a path/section-prefix
  argument to `EndpointResolver.resolver(...)`. NFS-e reuses only the `Ambiente` enum from here (no
  `UF`, no ini file): its single national URLs are hardcoded constants in `Sefaz4jNFSe`
  (`URL_PRODUCAO`/`URL_HOMOLOGACAO`), never routed through `EndpointResolver`.
- `net.accellog.sefaz4j.nfe.endpoints` — `Servico`; NFe-specific, not shared.
- `net.accellog.sefaz4j.cte.endpoints` — `Servico` (`CTE_RECEPCAO_SINC`/`CTE_CONSULTA_PROTOCOLO`/
  `CTE_RECEPCAO_EVENTO`); CTe-specific, not shared.
- `net.accellog.sefaz4j.mdfe.endpoints` — `Servico` (`MDFE_RECEPCAO`/`MDFE_RET_RECEPCAO`/
  `MDFE_CONSULTA_PROTOCOLO`/`RECEPCAO_EVENTO`); MDFe-specific, not shared.
- `net.accellog.sefaz4j.nfe.model` — **generated** JAXB classes (`TNFe`, `ObjectFactory`, etc.) — do not hand-edit, see below.
- `net.accellog.sefaz4j.cte.model` — **generated** JAXB classes (`TCTe`, `ObjectFactory`, etc.), from `cte_v4.00.xsd` — do not hand-edit.
- `net.accellog.sefaz4j.mdfe.model` — **generated** JAXB classes (`TMDFe`, `ObjectFactory`, etc.), from
  `mdfe_v3.00.xsd` — do not hand-edit.
- `net.accellog.sefaz4j.nfse.chave` — `DpsIdCalculator`: pure-concatenation `infDPS/@Id` builder, NOT
  shared with NFe/CTe's `chave` package (different algorithm, no check digit).
- `net.accellog.sefaz4j.nfse.xml` — `DpsXmlBuilder` (mirrors `NFeXmlBuilder`/`CTeXmlBuilder`: marshals
  `TCDPS` to DOM, injecting `infDPS/@Id` when absent) and `EventoNFSeXmlBuilder` (builds the 4-level
  `evento`/`infEvento`/`pedRegEvento`/`infPedReg` event structure — see the NFS-e section above).
- `net.accellog.sefaz4j.nfse.webservice` — `PayloadCompactado` (gzip+Base64 compress/decompress and
  JSON request wrapping), `RespostaNFSeParser`/`RespostaNFSe` (JSON response parsing, including the
  `erros` array business-rejection path); NFS-e-specific, not shared (no SOAP envelope builder here —
  there's no SOAP).
- `net.accellog.sefaz4j.nfse.model` — **generated** JAXB classes (`TCDPS`, `ObjectFactory`, etc.), from
  `DPS_v1.01.xsd` — do not hand-edit.
- `net.accellog.sefaz4j.mdfe.xml` — `MDFeXmlBuilder` (mirrors `NFeXmlBuilder`/`CTeXmlBuilder`: marshals
  `TMDFe` to DOM via JAXB, injecting `infMDFe/@Id` and `ide/cDV` from the computed chave when absent)
  and `EventoMDFeXmlBuilder` (builds the `eventoMDFe`/`infEvento` event structure by string
  concatenation — no JAXB — reusing NFe's `Id` format `"ID" + tpEvento + chave + nSeqEvento` padded to
  2 digits, unlike CTe's 3-digit padding).
- `net.accellog.sefaz4j.mdfe.webservice` — `SoapEnvelopeBuilder` (`envelopeRecepcao`/
  `envelopeRetRecepcao`/`envelopeConsultaSituacao`/`envelopeRecepcaoEvento`) and `ReciboPoller` (polls
  `MDFeRetRecepcao` while `cStat == 103`, the same role NFe's `ReciboPoller` has); MDFe-specific, not
  shared.

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
- **`src/main/resources/schemas/mdfe/consReciMDFeTiposBasico_v3.00.xsd` difere deliberadamente do
  arquivo oficial da SEFAZ**: o arquivo oficial redefine ali o `xs:complexType` `TProtMDFe`, já
  definido diretamente em `mdfeTiposBasico_v3.00.xsd` (que por sua vez inclui
  `tiposGeralMDFe_v3.00.xsd`, não o contrário) — um defeito de autoria genuíno da mesma categoria do
  já documentado acima para `leiauteCCe_v1.00.xsd`
  (`TCOrgaoIBGE` duplicado), que o `SchemaFactory`/validador XSD do Java rejeita com
  `sch-props-correct.2` (tipo duplicado no mesmo namespace). O bloco duplicado foi removido desta
  cópia bundled; para que `TRetConsReciMDFe.protMDFe` continue resolvendo `TProtMDFe` quando este
  arquivo é validado isoladamente (o XJC valida cada `schemaInclude` por si, não só o modelo
  combinado), um `<xs:include>` para `mdfeTiposBasico_v3.00.xsd` foi acrescentado no lugar do bloco
  removido — inofensivo, já que os outros `schemaInclude`s da mesma execução `xjc-mdfe` já o incluem,
  e o include duplicado de `tiposGeralMDFe_v3.00.xsd` que isso implica é idempotente (mesmo arquivo).
  A versão de referência original, com o defeito, continua em
  `Schemas/MDFe/consReciMDFeTiposBasico_v3.00.xsd`, fora do controle de versão — não copie esse
  arquivo de volta por cima do bundled.
- **`mdfeTiposBasico_v3.00.xsd`'s `infDoc/infMunDescarga/infCTe` declares `maxOccurs="20000"`**, above
  the JDK's default `jdk.xml.maxOccurLimit=5000` guard on *compiling* a schema (unrelated to
  validating an XML instance against it — this only governs how large a `maxOccurs` the schema
  document itself may declare). Without raising this, `SchemaFactory.newSchema(...)` throws on the
  very first MDF-e validation call in any downstream JVM that hasn't separately raised the limit via
  a launch flag — which a library consumer has no reason to know it needs to do. Two things fix this
  for the two places that compile this schema: `.mvn/jvm.config` sets `-Djdk.xml.maxOccurLimit=0` for
  Maven's own JVM (covering `generate-sources`/JAXB codegen and `mvn test`), and `ValidadorXsd` itself
  carries a static initializer that sets the same system property at runtime
  (`System.setProperty("jdk.xml.maxOccurLimit", "0")`, only if not already set, so an explicit
  caller-provided `-D` value is never silently overridden) — making `ValidadorXsd` self-contained
  regardless of the consuming application's own JVM flags.

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
