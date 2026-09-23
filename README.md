# Sefaz4j

Biblioteca Java pura (sem framework, sem `main`) para emissão e comunicação com a SEFAZ/ADN dos
documentos fiscais eletrônicos brasileiros. O objetivo final é cobrir **NFe**, **CTe**, **MDFe** e
**NFSe**; hoje estão implementados o **ciclo de vida completo da NFe 4.00**, o **ciclo de vida
completo do CTe 4.00** (emissão + consulta/cancelamento/CC-e — a inutilização foi descontinuada pela
SEFAZ para o CTe 4.00, ver seção do CTe abaixo), o **ciclo de vida completo do MDFe 3.00** (emissão +
consulta/cancelamento/encerramento/inclusão de condutor/inclusão de DF-e) e a **NFS-e Padrão Nacional** (emissão, consulta, cancelamento e cancelamento
por substituição, ver seção da NFS-e abaixo; confirmação/rejeição ainda não implementada). Além dos
documentos emitidos, a lib também faz a **Distribuição de DFe** (download de NF-e/CT-e destinados a
um CNPJ/CPF, pelo Ambiente Nacional) e a **Manifestação do Destinatário** da NF-e (Ciência da Operação).

O que o pacote de NFe já faz: monta o XML a partir de um objeto JAXB (`TNFe`), calcula a chave de
acesso, assina digitalmente (XML-DSig), valida contra a XSD oficial da SEFAZ, transmite via SOAP
sobre TLS mútuo (certificado A1) e, se o lote ficar "em processamento", faz o polling de
`NFeRetAutorizacao4` até obter o protocolo. Além da emissão, também cobre o ciclo de vida
pós-emissão: consulta de situação, cancelamento, Carta de Correção (CC-e) e inutilização de faixa
de numeração. Do lado do destinatário, envia a Manifestação "Ciência da Operação" (evento `210210`).

O pacote de CTe cobre a emissão — monta o XML a partir de um `TCTe` (JAXB), calcula a chave de
acesso, assina, valida contra a XSD oficial e transmite via `CTeRecepcaoSinc`, um serviço
**síncrono** (sem lote, sem `indSinc`, sem polling: a chamada HTTP já devolve o `cStat`/`protCTe`
definitivos) — e também o ciclo de vida pós-emissão: consulta de situação, cancelamento e Carta de
Correção (CC-e). A inutilização de faixa de numeração **não** está implementada para CTe: a SEFAZ a
descontinuou para o CTe 4.00 (não há chave `CTeInutilizacao_4.00` em nenhuma UF do `ACBrCTeServicos.ini`
de referência, e a própria ACBr recusa a chamada para essa versão).

O pacote de MDFe (Manifesto Eletrônico de Documentos Fiscais, `net.accellog.sefaz4j.mdfe`) é o
quarto documento coberto pela lib: monta o XML a partir de um `TMDFe` (JAXB), calcula a chave de
acesso, assina, valida contra a XSD oficial e transmite via `MDFeRecepcaoSinc` — um serviço
**síncrono**, igual ao `CTeRecepcaoSinc` do CTe, com o XML comprimido em gzip+Base64. (O
`MDFeRecepcao` de lote respondeu HTTP 404 na SVRS em testes reais e parece descontinuado; o polling
de `MDFeRetRecepcao` só entra como contingência, se a resposta ainda vier `103`.) Cobre também o
ciclo de vida pós-emissão: consulta de situação, cancelamento (evento `110111`), encerramento
(evento `110112`), inclusão de condutor (evento `110114`, o único dos quatro que não exige o número
de protocolo) e inclusão de DF-e (evento `110115`, para MDF-e de carga posterior).

O pacote de NFS-e (Padrão Nacional/SEFIN Nacional) é o mais diferente arquiteturalmente dos quatro: o
transporte é **REST+JSON, não SOAP**, e não há divisão por UF — um único endpoint nacional (ADN)
atende todos os municípios. O XML assinado da DPS (Declaração de Prestação de Serviços) é
comprimido em gzip, codificado em Base64 e enviado dentro de um JSON; a resposta traz a NFS-e pelo
caminho inverso. Cobre emissão, consulta de situação, cancelamento e cancelamento por substituição
(não existe Carta de Correção nesse padrão — a substituição cumpre esse papel); eventos de
confirmação/rejeição do tomador/intermediário ficam para uma etapa futura, fora deste escopo.

## Requisitos

- Java 25
- Maven 3.6+
- Um certificado digital A1 (arquivo `.pfx`/`.p12` + senha) para assinar e autenticar no mTLS —
  obrigatório mesmo em Homologação

## Como importar em outro projeto

O Sefaz4j é publicado no **GitHub Packages** a cada release
([github.com/accellogdev/Sefaz4j/releases](https://github.com/accellogdev/Sefaz4j/releases)), no
pacote `net.accellog:sefaz4j`.

1. Adicione o repositório do GitHub Packages ao `pom.xml` do projeto consumidor:

   ```xml
   <repositories>
       <repository>
           <id>github</id>
           <url>https://maven.pkg.github.com/accellogdev/Sefaz4j</url>
       </repository>
   </repositories>

   <dependency>
       <groupId>net.accellog</groupId>
       <artifactId>sefaz4j</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```

2. O GitHub Packages exige autenticação para *download* mesmo em repositórios públicos — não tem
   acesso anônimo para Maven. No `~/.m2/settings.xml` de quem for consumir, configure um servidor
   com `id` igual ao do `<repository>` acima e um
   [Personal Access Token](https://github.com/settings/tokens) com escopo `read:packages`:

   ```xml
   <servers>
       <server>
           <id>github</id>
           <username>SEU_USUARIO_GITHUB</username>
           <password>SEU_TOKEN_COM_read:packages</password>
       </server>
   </servers>
   ```

Alternativa sem usar o GitHub Packages: clonar o repositório e rodar `mvn clean install` para
instalar o jar no `~/.m2` local, com a mesma dependência acima.

## Uso rápido

```java
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.nfe.ResultadoEmissao;
import net.accellog.sefaz4j.nfe.Sefaz4jConfig;
import net.accellog.sefaz4j.nfe.Sefaz4jNFe;
import net.accellog.sefaz4j.endpoints.UF;
import net.accellog.sefaz4j.nfe.model.ObjectFactory;
import net.accellog.sefaz4j.nfe.model.TNFe;

import java.nio.file.Files;
import java.nio.file.Path;

byte[] pfxBytes = Files.readAllBytes(Path.of("/caminho/para/certificado.pfx"));

Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, pfxBytes, "senha-do-pfx");

// TNFe é gerado por JAXB a partir da XSD oficial da NFe 4.00 (net.accellog.sefaz4j.nfe.model) —
// monte-o com ObjectFactory preenchendo ide/emit/dest/det/total/transp/pag conforme o manual da NFe.
ObjectFactory fabrica = new ObjectFactory();
TNFe nfe = fabrica.createTNFe();
// ... preencher nfe.setInfNFe(...) com os grupos obrigatórios da NFe ...

ResultadoEmissao resultado = Sefaz4jNFe.emitir(config, nfe);

if (resultado.isOk()) {
    String xmlAutorizado = resultado.getXmlAutorizado(); // <nfeProc> pronto para persistir/DANFE
} else {
    // cStat/xMotivo != 100: rejeição de negócio da SEFAZ, não uma falha técnica
    System.out.println(resultado.getCStat() + " - " + resultado.getXMotivo());
}
```

Se o XML já vier assinado por outro processo, pule direto para validação+transmissão com
`Sefaz4jNFe.enviarXmlAssinado(config, xmlAssinado)`.

### Ciclo de vida pós-emissão

Além de `emitir`/`enviarXmlAssinado`, a fachada `Sefaz4jNFe` também cobre consulta, cancelamento,
Carta de Correção e inutilização — nenhum desses métodos usa JAXB, o XML é montado por
concatenação de string (mesmo estilo de `SoapEnvelopeBuilder`) e sempre validado contra a XSD
oficial antes de transmitir:

```java
// Consultar a situação de uma NFe já transmitida.
ResultadoConsulta consulta = Sefaz4jNFe.consultarSituacao(config, chaveAcesso);

// Cancelar uma NFe autorizada (chave de acesso, número do protocolo de
// autorização e uma justificativa com 15 a 255 caracteres).
ResultadoEvento cancelamento = Sefaz4jNFe.cancelar(config, chaveAcesso, nProtAutorizacao, justificativa);

// Emitir uma Carta de Correção (texto de correção com 15 a 1000 caracteres).
// O nSeqEvento (sequencial, padrão 1) é obrigatório a partir da segunda CC-e da mesma NFe.
ResultadoEvento cce = Sefaz4jNFe.corrigirCartaDeCorrecao(config, chaveAcesso, textoCorrecao);
ResultadoEvento segundaCce = Sefaz4jNFe.corrigirCartaDeCorrecao(config, chaveAcesso, textoCorrecao, 2);

// Inutilizar uma faixa de numeração não usada (não depende de uma NFe específica).
ResultadoInutilizacao inutilizacao = Sefaz4jNFe.inutilizar(
    config, cUF, ano, cnpj, serie, nNFIni, nNFFin, justificativa
);

// Manifestação do Destinatário — "Ciência da Operação" (evento 210210), assinada pelo
// destinatário da NF-e. Vai sempre para o Ambiente Nacional, independente da UF do config.
ResultadoManifestacao manifestacao = Sefaz4jNFe.manifestarCienciaDaOperacao(config, chaveAcesso, cnpjDestinatario);
ResultadoEvento eventoCiencia = manifestacao.getResultadoEvento(); // cStat 135 = registrado
String xmlEnviado = manifestacao.getXmlEnvio();
```

`ResultadoConsulta`, `ResultadoEvento` e `ResultadoInutilizacao` seguem o mesmo formato de
`ResultadoEmissao`: `isOk()` é estrito para o `cStat` de sucesso específico da operação (`100` para
consulta, `135` para cancelamento/CC-e, `102` para inutilização), com `getCStat()`/`getXMotivo()`
sempre preenchidos para inspecionar qualquer outro código. As mesmas três exceções técnicas do
fluxo de emissão (`CertificadoException`/`ValidacaoXsdException`/`ComunicacaoException`) valem
aqui; além disso, esses métodos validam localmente seus parâmetros (chave de acesso com 44
dígitos, tamanho de justificativa/texto de correção) e lançam `IllegalArgumentException` cedo
quando o chamador passa um valor malformado.

### Contrato de erros

`resultado.isOk()` só é `false` quando a SEFAZ processou o lote e devolveu um `cStat` diferente de
`100` (rejeição de negócio). Qualquer falha técnica é lançada como exceção, não convertida em
`ok=false`:

| Exceção | Quando ocorre |
|---|---|
| `net.accellog.sefaz4j.assinatura.CertificadoException` | PFX/senha inválidos, ou falha na assinatura |
| `net.accellog.sefaz4j.validacao.ValidacaoXsdException` | XML montado/informado não passa na XSD (`getViolacoes()`; o XML reprovado vem em `getXmlInvalido()`) |
| `net.accellog.sefaz4j.webservice.ComunicacaoException` | falha HTTP/SOAP, resposta da SEFAZ ilegível, ou lote ainda `103` após esgotar o polling — situação ambígua, não é seguro reenviar sem investigar |

Ajuste os parâmetros de polling e timeout com os setters fluentes de `Sefaz4jConfig`:
`setTimeout`, `setMaxTentativasPolling`, `setIntervaloPolling` (padrões: 30s, 5 tentativas, 5s).

## CTe

O CTe (Conhecimento de Transporte Eletrônico) é o segundo documento coberto pela lib
(`net.accellog.sefaz4j.cte`), com emissão e ciclo de vida pós-emissão (consulta/cancelamento/CC-e).
A API espelha a do NFe, mas a emissão é mais simples porque o serviço de recepção do CTe 4.00 é
síncrono: não há lote/`indSinc`/polling — a própria resposta HTTP já traz o `cStat`/`protCTe`
definitivos.

```java
import net.accellog.sefaz4j.cte.ResultadoEmissao;
import net.accellog.sefaz4j.cte.Sefaz4jConfig;
import net.accellog.sefaz4j.cte.Sefaz4jCTe;
import net.accellog.sefaz4j.cte.model.ObjectFactory;
import net.accellog.sefaz4j.cte.model.TCTe;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;

import java.nio.file.Files;
import java.nio.file.Path;

byte[] pfxBytes = Files.readAllBytes(Path.of("/caminho/para/certificado.pfx"));

Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, pfxBytes, "senha-do-pfx");

// TCTe é gerado por JAXB a partir da XSD oficial do CTe 4.00 (net.accellog.sefaz4j.cte.model) —
// monte-o com ObjectFactory preenchendo ide/emit/rem/dest/vPrest/imp/infCTeNorm conforme o
// manual do CT-e.
ObjectFactory fabrica = new ObjectFactory();
TCTe cte = fabrica.createTCTe();
// ... preencher cte.setInfCte(...) com os grupos obrigatórios do CT-e ...

ResultadoEmissao resultado = Sefaz4jCTe.emitir(config, cte);

if (resultado.isOk()) {
    String xmlAutorizado = resultado.getXmlAutorizado(); // <cteProc> pronto para persistir/DACTE
} else {
    // cStat/xMotivo != 100: rejeição de negócio da SEFAZ, não uma falha técnica
    System.out.println(resultado.getCStat() + " - " + resultado.getXMotivo());
}
```

Se o XML já vier assinado por outro processo, pule direto para validação+transmissão com
`Sefaz4jCTe.enviarXmlAssinado(config, xmlAssinado)`.

O conteúdo específico do modal de transporte (`infCTeNorm/infModal` — rodoviário, aéreo,
aquaviário, ferroviário, dutoviário) é declarado no XSD oficial como um `xs:any
processContents="skip"`: a lib não gera nem valida esse trecho, quem monta o `TCTe` constrói o
XML do modal escolhido por conta própria e injeta ali antes de chamar `emitir`.

As mesmas 3 exceções técnicas do NFe (`CertificadoException`/`ValidacaoXsdException`/
`ComunicacaoException`) valem para o CTe; como não há polling nesta fase, `ComunicacaoException`
não tem o caso de lote "ainda em processamento".

### Ciclo de vida pós-emissão (CTe)

Assim como no NFe, `Sefaz4jCTe` também cobre consulta, cancelamento e Carta de Correção — sem JAXB,
XML montado por concatenação de string e sempre validado contra a XSD oficial antes de transmitir.
A diferença mais visível para quem já usa o NFe: a Carta de Correção do CTe recebe uma lista
estruturada de correções (`List<InfCorrecao>`), não um texto livre — o XSD `evCCeCTe_v4.00.xsd` não
tem campo de texto livre, só o grupo repetível `grupoAlterado`/`campoAlterado`/`valorAlterado`
(+ `nroItemAlterado` opcional):

```java
import net.accellog.sefaz4j.cte.InfCorrecao;
import net.accellog.sefaz4j.cte.ResultadoConsulta;
import net.accellog.sefaz4j.cte.ResultadoEvento;

import java.util.List;

// Consultar a situação de um CT-e já transmitido.
ResultadoConsulta consulta = Sefaz4jCTe.consultarSituacao(config, chaveAcesso);

// Cancelar um CT-e autorizado (chave de acesso, número do protocolo de
// autorização e uma justificativa com 15 a 255 caracteres).
ResultadoEvento cancelamento = Sefaz4jCTe.cancelar(config, chaveAcesso, nProtAutorizacao, justificativa);

// Emitir uma Carta de Correção: lista de correções estruturadas, não texto livre.
List<InfCorrecao> correcoes = List.of(
    new InfCorrecao("ide", "xEmi", "Endereço do emitente corrigido") // 3-arg: nroItemAlterado fica null
);
ResultadoEvento cce = Sefaz4jCTe.corrigirCartaDeCorrecao(config, chaveAcesso, correcoes);
// O nSeqEvento (sequencial, padrão 1) é obrigatório a partir da segunda CC-e do mesmo CT-e.
ResultadoEvento segundaCce = Sefaz4jCTe.corrigirCartaDeCorrecao(config, chaveAcesso, correcoes, 2);
```

`ResultadoConsulta` e `ResultadoEvento` do CTe seguem o mesmo formato do `ResultadoEmissao`:
`isOk()` é estrito para o `cStat` de sucesso específico da operação (`100` para consulta, `135`
para cancelamento/CC-e), com `getCStat()`/`getXMotivo()` sempre preenchidos. Não há
`Sefaz4jCTe.inutilizar` — inutilização é permanentemente fora de escopo para CTe (ver acima).

## MDFe

O MDFe (Manifesto Eletrônico de Documentos Fiscais) é o quarto documento coberto pela lib
(`net.accellog.sefaz4j.mdfe`), com emissão e ciclo de vida pós-emissão (consulta/cancelamento/
encerramento/inclusão de condutor/inclusão de DF-e). A emissão usa o `MDFeRecepcaoSinc`
(**síncrono**, como o CTe): a própria resposta já traz o `cStat`/`protMDFe`. Se, excepcionalmente, a
resposta vier `103` ("em processamento"), `emitir` faz o polling de `MDFeRetRecepcao` como
contingência, com os mesmos parâmetros de polling do NFe.

```java
import net.accellog.sefaz4j.mdfe.ResultadoEmissao;
import net.accellog.sefaz4j.mdfe.Sefaz4jConfig;
import net.accellog.sefaz4j.mdfe.Sefaz4jMDFe;
import net.accellog.sefaz4j.mdfe.model.ObjectFactory;
import net.accellog.sefaz4j.mdfe.model.TMDFe;
import net.accellog.sefaz4j.endpoints.Ambiente;
import net.accellog.sefaz4j.endpoints.UF;

import java.nio.file.Files;
import java.nio.file.Path;

byte[] pfxBytes = Files.readAllBytes(Path.of("/caminho/para/certificado.pfx"));

Sefaz4jConfig config = new Sefaz4jConfig(UF.SP, Ambiente.HOMOLOGACAO, pfxBytes, "senha-do-pfx");

// TMDFe é gerado por JAXB a partir da XSD oficial do MDFe 3.00 (net.accellog.sefaz4j.mdfe.model) —
// monte-o com ObjectFactory preenchendo ide/emit/infModal/infDoc/tot conforme o manual do MDF-e.
ObjectFactory fabrica = new ObjectFactory();
TMDFe mdfe = fabrica.createTMDFe();
// ... preencher mdfe.setInfMDFe(...) com os grupos obrigatórios do MDF-e ...

ResultadoEmissao resultado = Sefaz4jMDFe.emitir(config, mdfe);

if (resultado.isOk()) {
    String xmlAutorizado = resultado.getXmlAutorizado(); // <mdfeProc> pronto para persistir/DAMDFE
} else {
    // cStat/xMotivo != 100: rejeição de negócio da SEFAZ, não uma falha técnica
    System.out.println(resultado.getCStat() + " - " + resultado.getXMotivo());
}
```

Se o XML já vier assinado por outro processo, pule direto para validação+transmissão(+polling) com
`Sefaz4jMDFe.enviarXmlAssinado(config, xmlAssinado)`.

O conteúdo específico do modal de transporte (`infModal` — rodoviário, aéreo, aquaviário,
ferroviário) é declarado no XSD oficial como um `xs:any processContents="skip"`, igual ao CTe: a
lib não gera nem valida esse trecho, quem monta o `TMDFe` constrói o XML do modal escolhido por
conta própria e injeta ali antes de chamar `emitir`.

As mesmas 3 exceções técnicas do NFe (`CertificadoException`/`ValidacaoXsdException`/
`ComunicacaoException`) valem para o MDFe, incluindo o caso de "ainda em processamento" após
esgotar o polling de contingência (mesmo risco de reenvio às cegas do NFe).

### Ciclo de vida pós-emissão (MDFe)

Assim como no NFe/CTe, `Sefaz4jMDFe` também cobre consulta e quatro eventos — sem JAXB, XML montado
por concatenação de string e sempre validado contra a XSD oficial antes de transmitir. Não existe
Carta de Correção para o MDFe; os quatro eventos são cancelamento, encerramento (evento específico do
MDFe, para registrar onde a viagem realmente terminou), inclusão de condutor e inclusão de DF-e:

```java
import net.accellog.sefaz4j.mdfe.InfDocInclusao;
import net.accellog.sefaz4j.mdfe.ResultadoConsulta;
import net.accellog.sefaz4j.mdfe.ResultadoEvento;

import java.util.List;

// Consultar a situação de um MDF-e já transmitido.
ResultadoConsulta consulta = Sefaz4jMDFe.consultarSituacao(config, chaveAcesso);

// Cancelar um MDF-e autorizado (chave de acesso, número do protocolo de
// autorização e uma justificativa com 15 a 255 caracteres).
ResultadoEvento cancelamento = Sefaz4jMDFe.cancelar(config, chaveAcesso, nProtAutorizacao, justificativa);

// Encerrar o MDF-e: informar onde/quando a viagem terminou (pode ser diferente
// da UF/município previstos originalmente em ide).
ResultadoEvento encerramento = Sefaz4jMDFe.encerrar(
    config, chaveAcesso, nProtAutorizacao, cUFEncerramento, cMunEncerramento, dataEncerramento
);

// Incluir um condutor no MDF-e (não exige número de protocolo).
ResultadoEvento condutor = Sefaz4jMDFe.incluirCondutor(config, chaveAcesso, "Nome do Condutor", cpfCondutor);
// Um segundo condutor no mesmo MDF-e precisa do nSeqEvento explícito (padrão 1 na chamada acima).
ResultadoEvento segundoCondutor = Sefaz4jMDFe.incluirCondutor(config, chaveAcesso, "Outro Condutor", outroCpf, 2);

// Incluir NF-es num MDF-e emitido com carga posterior (ide/indCarregaPosterior = 1): informa o
// município de carregamento e, para cada NF-e, o município de descarga. Só aceita chaves de NF-e —
// o evento 110115 não tem campo para CT-e.
List<InfDocInclusao> documentos = List.of(
    new InfDocInclusao(cMunDescarga, "Nome do Município de Descarga", chaveNFe)
);
ResultadoEvento inclusaoDFe = Sefaz4jMDFe.incluirDFe(
    config, chaveAcesso, nProtAutorizacao, cMunCarrega, "Nome do Município de Carga", documentos
);
// Uma segunda inclusão no mesmo MDF-e precisa do nSeqEvento explícito.
ResultadoEvento segundaInclusao = Sefaz4jMDFe.incluirDFe(
    config, chaveAcesso, nProtAutorizacao, cMunCarrega, "Nome do Município de Carga", outrosDocumentos, 2
);
```

`ResultadoConsulta` e `ResultadoEvento` do MDFe seguem o mesmo formato do `ResultadoEmissao`:
`isOk()` é estrito para o `cStat` de sucesso específico da operação (`100` para consulta, `135`
para qualquer um dos quatro eventos), com `getCStat()`/`getXMotivo()` sempre preenchidos.

## NFS-e

A NFS-e Padrão Nacional (SEFIN Nacional/ADN) é o documento mais diferente arquiteturalmente da lib
(`net.accellog.sefaz4j.nfse`): o transporte é **REST+JSON, não SOAP**, e não há divisão por UF — um
único endpoint nacional atende todos os municípios, então `Sefaz4jConfig` da NFS-e não recebe `UF`
no construtor. O XML assinado da DPS (Declaração de Prestação de Serviços) é comprimido em gzip,
codificado em Base64 e enviado dentro de um JSON.

```java
import net.accellog.sefaz4j.nfse.ResultadoEmissao;
import net.accellog.sefaz4j.nfse.Sefaz4jConfig;
import net.accellog.sefaz4j.nfse.Sefaz4jNFSe;
import net.accellog.sefaz4j.nfse.model.ObjectFactory;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.endpoints.Ambiente;

import java.nio.file.Files;
import java.nio.file.Path;

byte[] pfxBytes = Files.readAllBytes(Path.of("/caminho/para/certificado.pfx"));

// Sem UF no construtor: a NFS-e Padrão Nacional tem um único endpoint nacional.
Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "senha-do-pfx");

// TCDPS é gerado por JAXB a partir da XSD oficial da DPS 1.01 (net.accellog.sefaz4j.nfse.model) —
// monte-o com ObjectFactory preenchendo infDPS conforme o manual da NFS-e.
ObjectFactory fabrica = new ObjectFactory();
TCDPS dps = fabrica.createTCDPS();
// ... preencher dps.setInfDPS(...) com os grupos obrigatórios da DPS ...

ResultadoEmissao resultado = Sefaz4jNFSe.emitir(config, dps);

if (resultado.isOk()) {
    String chaveAcesso = resultado.getChaveAcesso(); // só existe depois de emitida com sucesso
    String xmlAutorizado = resultado.getXmlAutorizado();
} else {
    // cStat/mensagem != sucesso: rejeição de negócio do ADN, não uma falha técnica
    System.out.println(resultado.getCStat() + " - " + resultado.getMensagem());
}
```

Se o XML já vier assinado por outro processo, pule direto para validação+transmissão com
`Sefaz4jNFSe.enviarXmlAssinado(config, xmlAssinado)`.

Diferente de NFe/CTe/MDFe, os resultados da NFS-e expõem `getMensagem()` em vez de `getXMotivo()`
(mesmo nome do campo na resposta JSON do ADN); as mesmas 3 exceções técnicas
(`CertificadoException`/`ValidacaoXsdException`/`ComunicacaoException`) valem aqui também.

### Ciclo de vida pós-emissão (NFS-e)

Não existe Carta de Correção neste padrão — a substituição cumpre esse papel. `Sefaz4jNFSe` cobre
consulta, cancelamento simples e cancelamento por substituição:

```java
import net.accellog.sefaz4j.nfse.ResultadoConsulta;
import net.accellog.sefaz4j.nfse.ResultadoEvento;
import net.accellog.sefaz4j.nfse.model.TCSubstituicao;

// Consultar a situação de uma NFS-e já transmitida (chaveAcesso vem de
// resultado.getChaveAcesso() da emissão, nunca é calculada localmente).
ResultadoConsulta consulta = Sefaz4jNFSe.consultarSituacao(config, chaveAcesso);

// Cancelar uma NFS-e autorizada: cMotivo é 1 (Erro na Emissão), 2 (Serviço não
// Prestado) ou 9 (Outros); cnpjAutor é explícito (não é derivado da chave, ao
// contrário de NFe/CTe/MDFe, porque o layout de bytes da chNFSe não é público).
ResultadoEvento cancelamento = Sefaz4jNFSe.cancelar(config, chaveAcesso, cnpjAutor, 2, "Serviço não foi prestado");

// Cancelar por substituição: emite a DPS substituta e só envia o evento de
// cancelamento se a nova emissão for autorizada. cMotivo/xMotivo do evento vêm
// de dpsSubstituta.getInfDPS().getSubst(), não são parâmetros separados.
ObjectFactory fabrica = new ObjectFactory();
TCSubstituicao subst = fabrica.createTCSubstituicao();
subst.setChSubstda(chaveAcesso);
subst.setCMotivo("01"); // TSCodJustSubst: "01" a "05" ou "99"
TCDPS dpsSubstituta = fabrica.createTCDPS();
// ... preencher dpsSubstituta.setInfDPS(...) e infDPS.setSubst(subst) ...
ResultadoEvento substituicao = Sefaz4jNFSe.cancelarPorSubstituicao(config, chaveAcesso, cnpjAutor, dpsSubstituta);
```

`ResultadoConsulta` e `ResultadoEvento` da NFS-e seguem o mesmo formato do `ResultadoEmissao`:
`isOk()`/`getCStat()`/`getMensagem()` sempre preenchidos. Não há `Sefaz4jNFSe.inutilizar` (o padrão
não tem esse conceito) nem eventos de confirmação/rejeição do tomador/intermediário (fora de escopo
desta lib por enquanto).

## Distribuição de DFe (NF-e e CT-e)

`net.accellog.sefaz4j.distdfe` baixa do Ambiente Nacional os documentos (NF-e/CT-e, resumos e
eventos) destinados a um CNPJ/CPF. A mesma fachada `Sefaz4jDistDfe` atende NF-e e CT-e; o tipo é
escolhido pelo enum `TipoDocumentoDistDfe` (`NFE`/`CTE`). O `Sefaz4jConfig` deste pacote **não
recebe UF**: o endpoint é sempre o nacional.

```java
import net.accellog.sefaz4j.distdfe.DocumentoDistDfe;
import net.accellog.sefaz4j.distdfe.ResultadoDistribuicaoDFe;
import net.accellog.sefaz4j.distdfe.Sefaz4jConfig;
import net.accellog.sefaz4j.distdfe.Sefaz4jDistDfe;
import net.accellog.sefaz4j.distdfe.TipoDocumentoDistDfe;
import net.accellog.sefaz4j.endpoints.Ambiente;

Sefaz4jConfig config = new Sefaz4jConfig(Ambiente.HOMOLOGACAO, pfxBytes, "senha-do-pfx");

// Busca incremental a partir do último NSU já processado ("0" na primeira vez).
// cUFAutor é opcional para NF-e (null) e obrigatório para CT-e.
ResultadoDistribuicaoDFe resultado = Sefaz4jDistDfe.distribuicaoPorUltNSU(
    TipoDocumentoDistDfe.NFE, config, null, cnpjInteressado, ultNSU
);

for (DocumentoDistDfe doc : resultado.getDocumentos()) {
    // doc.getXml() já vem descompactado (docZip); doc.getSchema() diz o tipo (resumo, NF-e completa, evento...)
    processar(doc.getNsu(), doc.getSchema(), doc.getXml());
}

String proximoUltNSU = resultado.getUltNSU(); // persistir para a próxima chamada
if (resultado.temMaisDocumentos()) {          // cStat 138: repetir a chamada com o novo ultNSU
} else if (resultado.nadaMaisADistribuir()) { // cStat 137: aguardar antes de consultar de novo
} else if (resultado.consumoIndevido()) {     // cStat 656: a SEFAZ bloqueou por excesso de consultas
}

// Também é possível buscar um NSU específico ou um documento pela chave de acesso:
Sefaz4jDistDfe.distribuicaoPorNSU(TipoDocumentoDistDfe.CTE, config, cUFAutor, cnpjInteressado, nsu);
Sefaz4jDistDfe.distribuicaoPorChave(TipoDocumentoDistDfe.NFE, config, null, cnpjInteressado, chaveAcesso);
```

`getXmlEnvio()`/`getXmlRetorno()` trazem o XML bruto da requisição/resposta, para auditoria/log. As
mesmas exceções técnicas (`ValidacaoXsdException`/`ComunicacaoException`) valem aqui. Não há
assinatura XML: o pedido `distDFeInt` não é assinado, e a autenticação é só o TLS mútuo com o
certificado A1.

## Como testar

```bash
mvn test                                  # suíte padrão (JUnit 4) — não acessa a rede
mvn test -Dtest=NomeDaClasse#metodo       # um teste específico
mvn test -Pintegration-tests              # também roda os testes de integração com a Homologação real
```

O `mvn test` sozinho nunca sai para a rede: `HomologacaoIntegrationTest` (NFe, em
`src/test/java/.../nfe/integration/`), `HomologacaoIntegrationTestCTe` (CTe, em
`src/test/java/.../cte/integration/`) e `Sefaz4jDistDfeIntegrationTest` (Distribuição de DFe, em
`src/test/java/.../distdfe/integration/`) são excluídos por padrão pela configuração do
`maven-surefire-plugin` e só são incluídos com o profile `integration-tests`. Mesmo assim, eles se
autoexcluem (`org.junit.Assume`) a menos que as variáveis de ambiente `SEFAZ4J_TEST_PFX_PATH` e
`SEFAZ4J_TEST_PFX_SENHA` apontem para um certificado A1 real (o mesmo certificado serve para
todos). O teste de Distribuição de DFe exige também `SEFAZ4J_TEST_CNPJ`, o CNPJ titular do
certificado:

```bash
export SEFAZ4J_TEST_PFX_PATH=/caminho/para/certificado-homologacao.pfx
export SEFAZ4J_TEST_PFX_SENHA=senha-do-certificado
export SEFAZ4J_TEST_CNPJ=12345678000199   # só para o teste de Distribuição de DFe
mvn test -Pintegration-tests
```

## Estrutura de pacotes

Um consumidor de NFe precisa do pacote raiz `net.accellog.sefaz4j.nfe` (a fachada `Sefaz4jNFe` e os
tipos de config/resultado); um consumidor de CTe, do `net.accellog.sefaz4j.cte` (fachada
`Sefaz4jCTe`, com emissão e ciclo de vida pós-emissão); um consumidor de NFS-e, do
`net.accellog.sefaz4j.nfse` (fachada `Sefaz4jNFSe`); um consumidor de MDFe, do
`net.accellog.sefaz4j.mdfe` (fachada `Sefaz4jMDFe`); para baixar documentos destinados, o
`net.accellog.sefaz4j.distdfe` (fachada `Sefaz4jDistDfe`). Os demais pacotes são implementação interna, e
as peças de infraestrutura genéricas (`chave`, `assinatura`, `validacao`, `webservice`, `endpoints`)
ficam na raiz e são compartilhadas pelos quatro documentos (NFS-e é a exceção parcial — reusa só o
`Ambiente` de `endpoints`, sem `UF` nem arquivo `.ini`, e tem seu próprio pacote `nfse.chave`):

- `chave` — cálculo da chave de acesso de 44 dígitos (mod-11)
- `nfe.xml` / `cte.xml` / `mdfe.xml` — montagem do DOM a partir do `TNFe`/`TCTe`/`TMDFe`
- `assinatura` — assinatura XML-DSig (Apache Santuario) e carregamento do certificado A1
- `validacao` — validação contra a XSD oficial (`nfe_v4.00.xsd`, `cte_v4.00.xsd`, `DPS_v1.01.xsd` ou
  `mdfe_v3.00.xsd`, conforme o documento)
- `webservice` — cliente HTTP com TLS mútuo e parsing da resposta, compartilhados (inclui a
  compressão gzip+Base64 da recepção síncrona de CTe/MDFe e a complementação automática da cadeia
  de certificados, via AIA, quando o PFX traz só o certificado final); a montagem do envelope SOAP
  e o polling (NFe; no MDFe só como contingência) ficam em
  `nfe.webservice`/`cte.webservice`/`mdfe.webservice`
- `endpoints` — resolução de URL por UF/Ambiente/Serviço (`nfe-servicos.ini`/`cte-servicos.ini`/
  `mdfe-servicos.ini`); a pseudo-UF `UF.AN` representa o Ambiente Nacional (manifestação e
  Distribuição de DFe)
- `distdfe` — Distribuição de DFe (NF-e/CT-e): montagem do `distDFeInt` e parsing/descompactação do
  `retDistDFeInt`
- `model` — classes **geradas** por JAXB a partir das XSDs (não editar à mão)

## Roadmap

- [x] NFe — envio/autorização (4.00)
- [x] NFe — ciclo de vida pós-emissão (consulta/cancelamento/CC-e/inutilização)
- [x] NFe — Manifestação do Destinatário: Ciência da Operação (`210210`)
- [x] CTe — emissão (4.00)
- [x] CTe — ciclo de vida pós-emissão (consulta/cancelamento/CC-e — inutilização descontinuada pela SEFAZ)
- [x] MDFe — emissão (3.00, recepção síncrona)
- [x] MDFe — ciclo de vida pós-emissão (consulta/cancelamento/encerramento/inclusão de condutor/inclusão de DF-e)
- [x] NFSe — emissão, consulta, cancelamento e cancelamento por substituição (Padrão Nacional)
- [x] Distribuição de DFe — NF-e e CT-e (por último NSU, por NSU e por chave)
- [ ] NFSe — eventos de confirmação/rejeição do tomador/intermediário
