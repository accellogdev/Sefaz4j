# Sefaz4j

Biblioteca Java pura (sem framework, sem `main`) para emissão e comunicação com a SEFAZ/ADN dos
documentos fiscais eletrônicos brasileiros. O objetivo final é cobrir **NFe**, **CTe**, **MDFe** e
**NFSe**; hoje estão implementados o **ciclo de vida completo da NFe 4.00**, o **ciclo de vida
completo do CTe 4.00** (emissão + consulta/cancelamento/CC-e — a inutilização foi descontinuada pela
SEFAZ para o CTe 4.00, ver seção do CTe abaixo), o **ciclo de vida completo do MDFe 3.00** (emissão +
consulta/cancelamento/encerramento/inclusão de condutor — inutilização nunca existiu para o MDFe, ver
seção do MDFe abaixo) e a **NFS-e Padrão Nacional** (emissão, consulta, cancelamento e cancelamento
por substituição, ver seção da NFS-e abaixo; confirmação/rejeição ainda não implementada).

O que o pacote de NFe já faz: monta o XML a partir de um objeto JAXB (`TNFe`), calcula a chave de
acesso, assina digitalmente (XML-DSig), valida contra a XSD oficial da SEFAZ, transmite via SOAP
sobre TLS mútuo (certificado A1) e, se o lote ficar "em processamento", faz o polling de
`NFeRetAutorizacao4` até obter o protocolo. Além da emissão, também cobre o ciclo de vida
pós-emissão: consulta de situação, cancelamento, Carta de Correção (CC-e) e inutilização de faixa
de numeração.

O pacote de CTe cobre a emissão — monta o XML a partir de um `TCTe` (JAXB), calcula a chave de
acesso, assina, valida contra a XSD oficial e transmite via `CTeRecepcaoSinc`, um serviço
**síncrono** (sem lote, sem `indSinc`, sem polling: a chamada HTTP já devolve o `cStat`/`protCTe`
definitivos) — e também o ciclo de vida pós-emissão: consulta de situação, cancelamento e Carta de
Correção (CC-e). A inutilização de faixa de numeração **não** está implementada para CTe: a SEFAZ a
descontinuou para o CTe 4.00 (não há chave `CTeInutilizacao_4.00` em nenhuma UF do `ACBrCTeServicos.ini`
de referência, e a própria ACBr recusa a chamada para essa versão).

O pacote de MDFe (Manifesto Eletrônico de Documentos Fiscais, `net.accellog.sefaz4j.mdfe`) é o
quarto documento coberto pela lib e arquiteturalmente mais próximo do NFe do que do CTe: monta o
XML a partir de um `TMDFe` (JAXB), calcula a chave de acesso, assina, valida contra a XSD oficial e
transmite via `MDFeRecepcao` — um serviço de **lote**, igual ao NFe (não síncrono como o
`CTeRecepcaoSinc`) — fazendo o polling de `MDFeRetRecepcao` quando o lote fica "em processamento".
Cobre também o ciclo de vida pós-emissão: consulta de situação, cancelamento (evento `110111`),
encerramento (evento `110112`) e inclusão de condutor (evento `110114`, o único dos três que não
exige o número de protocolo). Não existe inutilização para o MDFe: diferente do CTe, onde a SEFAZ
apenas descontinuou o recurso na versão 4.00, a inutilização nunca fez parte do padrão MDFe (confirmado
pela ausência total na implementação de referência ACBr).

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
       <version>1.0.0-alpha-1</version>
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
| `net.accellog.sefaz4j.validacao.ValidacaoXsdException` | XML montado/informado não passa na XSD (`getViolacoes()`) |
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

## Como testar

```bash
mvn test                                  # suíte padrão (JUnit 4) — não acessa a rede
mvn test -Dtest=NomeDaClasse#metodo       # um teste específico
mvn test -Pintegration-tests              # também roda o teste de integração com a Homologação real
```

O `mvn test` sozinho nunca sai para a rede: `HomologacaoIntegrationTest` (NFe, em
`src/test/java/.../nfe/integration/`) e `HomologacaoIntegrationTestCTe` (CTe, em
`src/test/java/.../cte/integration/`) são excluídos por padrão pela configuração do
`maven-surefire-plugin` e só são incluídos com o profile `integration-tests`. Mesmo assim, eles se
autoexcluem (`org.junit.Assume`) a menos que as variáveis de ambiente `SEFAZ4J_TEST_PFX_PATH` e
`SEFAZ4J_TEST_PFX_SENHA` apontem para um certificado A1 real (o mesmo certificado serve para os
dois documentos) — ou seja, eles só chegam a acessar a SEFAZ de Homologação de verdade se você
fornecer essas duas variáveis:

```bash
export SEFAZ4J_TEST_PFX_PATH=/caminho/para/certificado-homologacao.pfx
export SEFAZ4J_TEST_PFX_SENHA=senha-do-certificado
mvn test -Pintegration-tests
```

## Estrutura de pacotes (NFe e CTe)

Um consumidor de NFe precisa do pacote raiz `net.accellog.sefaz4j.nfe` (a fachada `Sefaz4jNFe` e os
tipos de config/resultado); um consumidor de CTe, do `net.accellog.sefaz4j.cte` (fachada
`Sefaz4jCTe`, com emissão e ciclo de vida pós-emissão). Os demais pacotes são implementação
interna, e as peças de infraestrutura genéricas (`chave`, `assinatura`, `validacao`, `webservice`,
`endpoints`) ficam na raiz e são compartilhadas pelos dois documentos:

- `chave` — cálculo da chave de acesso de 44 dígitos (mod-11)
- `nfe.xml` / `cte.xml` — montagem do DOM a partir do `TNFe`/`TCTe`
- `assinatura` — assinatura XML-DSig (Apache Santuario) e carregamento do certificado A1
- `validacao` — validação contra a XSD oficial (`nfe_v4.00.xsd` ou `cte_v4.00.xsd`, conforme o documento)
- `webservice` — cliente HTTP com TLS mútuo e parsing da resposta, compartilhados; a montagem do
  envelope SOAP e o polling de `NFeRetAutorizacao4` (só NFe — o CTe é síncrono) ficam em
  `nfe.webservice`/`cte.webservice`
- `endpoints` — resolução de URL por UF/Ambiente/Serviço (`nfe-servicos.ini`/`cte-servicos.ini`)
- `model` — classes **geradas** por JAXB a partir das XSDs (não editar à mão)

## Roadmap

- [x] NFe — envio/autorização (4.00)
- [x] NFe — ciclo de vida pós-emissão (consulta/cancelamento/CC-e/inutilização)
- [x] CTe — emissão (4.00)
- [x] CTe — ciclo de vida pós-emissão (consulta/cancelamento/CC-e — inutilização descontinuada pela SEFAZ)
- [x] MDFe — emissão (3.00)
- [x] MDFe — ciclo de vida pós-emissão (consulta/cancelamento/encerramento/inclusão de condutor — inutilização nunca existiu no padrão)
- [ ] NFSe
