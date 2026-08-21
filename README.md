# Sefaz4j

Biblioteca Java pura (sem framework, sem `main`) para emissão e comunicação com a SEFAZ dos
documentos fiscais eletrônicos brasileiros. O objetivo final é cobrir **NFe**, **CTe**, **MDFe** e
**NFSe**, mas hoje só o fluxo de **envio/autorização de NFe 4.00** está implementado — os demais
documentos ainda não têm código neste repositório.

O que o pacote de NFe já faz: monta o XML a partir de um objeto JAXB (`TNFe`), calcula a chave de
acesso, assina digitalmente (XML-DSig), valida contra a XSD oficial da SEFAZ, transmite via SOAP
sobre TLS mútuo (certificado A1) e, se o lote ficar "em processamento", faz o polling de
`NFeRetAutorizacao4` até obter o protocolo.

## Requisitos

- Java 25
- Maven 3.6+
- Um certificado digital A1 (arquivo `.pfx`/`.p12` + senha) para assinar e autenticar no mTLS —
  obrigatório mesmo em Homologação

## Como importar em outro projeto

O código-fonte fica em [github.com/accellogdev/Sefaz4j](https://github.com/accellogdev/Sefaz4j),
mas o Sefaz4j **não é publicado em nenhum repositório Maven remoto** (nem Central, nem GitHub
Packages) — é preciso clonar e instalar no repositório Maven local (`~/.m2`) antes de usá-lo como
dependência em outro projeto.

1. Clone o repositório e instale o jar no repositório local:

   ```bash
   git clone https://github.com/accellogdev/Sefaz4j.git
   cd Sefaz4j
   mvn clean install
   ```

   Isso também dispara a geração de classes JAXB (`generate-sources`) e roda a suíte de testes
   (veja a seção [Como testar](#como-testar) para saltar isso, se necessário).

2. No `pom.xml` do projeto consumidor, adicione a dependência com o mesmo `groupId`/`artifactId`/
   `version` deste `pom.xml`:

   ```xml
   <dependency>
       <groupId>net.accellog</groupId>
       <artifactId>Sefaz4j</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```

Sempre que uma nova versão for publicada no GitHub (nova tag/release), repita `git pull` +
`mvn clean install` e atualize a versão no projeto consumidor — não há resolução automática de
dependência transitiva via GitHub, o `~/.m2` local é a única "fonte" que o Maven do consumidor vê.

Alternativa sem instalar no `~/.m2`: usar este repositório como módulo de um projeto Maven
multi-módulo (reactor), declarando-o como `<module>` no `pom.xml` pai — útil se os dois projetos
vivem no mesmo monorepo/workspace e você não quer depender do cache local.

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

### Contrato de erros

`resultado.isOk()` só é `false` quando a SEFAZ processou o lote e devolveu um `cStat` diferente de
`100` (rejeição de negócio). Qualquer falha técnica é lançada como exceção, não convertida em
`ok=false`:

| Exceção | Quando ocorre |
|---|---|
| `net.accellog.sefaz4j.nfe.assinatura.CertificadoException` | PFX/senha inválidos, ou falha na assinatura |
| `net.accellog.sefaz4j.nfe.validacao.ValidacaoXsdException` | XML montado/informado não passa na XSD (`getViolacoes()`) |
| `net.accellog.sefaz4j.nfe.webservice.ComunicacaoException` | falha HTTP/SOAP, resposta da SEFAZ ilegível, ou lote ainda `103` após esgotar o polling — situação ambígua, não é seguro reenviar sem investigar |

Ajuste os parâmetros de polling e timeout com os setters fluentes de `Sefaz4jConfig`:
`setTimeout`, `setMaxTentativasPolling`, `setIntervaloPolling` (padrões: 30s, 5 tentativas, 5s).

## Como testar

```bash
mvn test                                  # suíte padrão (JUnit 4) — não acessa a rede
mvn test -Dtest=NomeDaClasse#metodo       # um teste específico
mvn test -Pintegration-tests              # também roda o teste de integração com a Homologação real
```

O `mvn test` sozinho nunca sai para a rede: `HomologacaoIntegrationTest` (em
`src/test/java/.../nfe/integration/`) é excluído por padrão pela configuração do
`maven-surefire-plugin` e só é incluído com o profile `integration-tests`. Mesmo assim, ele se
autoexclui (`org.junit.Assume`) a menos que as variáveis de ambiente `SEFAZ4J_TEST_PFX_PATH` e
`SEFAZ4J_TEST_PFX_SENHA` apontem para um certificado A1 real — ou seja, ele só chega a acessar a
SEFAZ de Homologação de verdade se você fornecer essas duas variáveis:

```bash
export SEFAZ4J_TEST_PFX_PATH=/caminho/para/certificado-homologacao.pfx
export SEFAZ4J_TEST_PFX_SENHA=senha-do-certificado
mvn test -Pintegration-tests
```

## Estrutura de pacotes (NFe)

Tudo que um consumidor precisa está no pacote raiz `net.accellog.sefaz4j.nfe` (a fachada
`Sefaz4jNFe` e os tipos de config/resultado). Os demais pacotes são implementação interna:

- `chave` — cálculo da chave de acesso de 44 dígitos (mod-11)
- `xml` — montagem do DOM a partir do `TNFe`
- `assinatura` — assinatura XML-DSig (Apache Santuario) e carregamento do certificado A1
- `validacao` — validação contra a XSD oficial `nfe_v4.00.xsd`
- `webservice` — cliente HTTP com TLS mútuo, montagem do envelope SOAP, parsing da resposta e
  polling de `NFeRetAutorizacao4`
- `endpoints` — resolução de URL por UF/Ambiente/Serviço
- `model` — classes **geradas** por JAXB a partir das XSDs (não editar à mão)

## Roadmap

- [x] NFe — envio/autorização (4.00)
- [ ] CTe
- [ ] MDFe
- [ ] NFSe
