# Sefaz4j

Biblioteca Java pura (sem framework, sem `main`) para emissão e comunicação com a SEFAZ dos
documentos fiscais eletrônicos brasileiros. O objetivo final é cobrir **NFe**, **CTe**, **MDFe** e
**NFSe**; hoje estão implementados o **ciclo de vida completo da NFe 4.00** e a **emissão do CTe
4.00** — MDFe e NFSe, e o ciclo de vida pós-emissão do CTe, ainda não têm código neste repositório.

O que o pacote de NFe já faz: monta o XML a partir de um objeto JAXB (`TNFe`), calcula a chave de
acesso, assina digitalmente (XML-DSig), valida contra a XSD oficial da SEFAZ, transmite via SOAP
sobre TLS mútuo (certificado A1) e, se o lote ficar "em processamento", faz o polling de
`NFeRetAutorizacao4` até obter o protocolo. Além da emissão, também cobre o ciclo de vida
pós-emissão: consulta de situação, cancelamento, Carta de Correção (CC-e) e inutilização de faixa
de numeração.

O pacote de CTe cobre, por enquanto, só a emissão: monta o XML a partir de um `TCTe` (JAXB), calcula
a chave de acesso, assina, valida contra a XSD oficial e transmite via `CTeRecepcaoSinc` — um
serviço **síncrono** (sem lote, sem `indSinc`, sem polling: a chamada HTTP já devolve o `cStat`/
`protCTe` definitivos).

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
import net.accellog.sefaz4j.nfe.Ambiente;
import net.accellog.sefaz4j.nfe.ResultadoEmissao;
import net.accellog.sefaz4j.nfe.Sefaz4jConfig;
import net.accellog.sefaz4j.nfe.Sefaz4jNFe;
import net.accellog.sefaz4j.nfe.endpoints.UF;
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

O CTe (Conhecimento de Transporte Eletrônico) é o segundo documento coberto pela lib — por
enquanto só a emissão (`net.accellog.sefaz4j.cte`, fase 1). A API espelha a do NFe, mas é mais
simples porque o serviço de recepção do CTe 4.00 é síncrono: não há lote/`indSinc`/polling — a
própria resposta HTTP já traz o `cStat`/`protCTe` definitivos.

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
`Sefaz4jCTe`, fase 1 só com emissão). Os demais pacotes são implementação interna, e as peças de
infraestrutura genéricas (`chave`, `assinatura`, `validacao`, `webservice`, `endpoints`) ficam na
raiz e são compartilhadas pelos dois documentos:

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
- [ ] CTe — ciclo de vida pós-emissão
- [ ] MDFe
- [ ] NFSe
