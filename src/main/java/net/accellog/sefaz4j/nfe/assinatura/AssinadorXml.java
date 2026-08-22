package net.accellog.sefaz4j.nfe.assinatura;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

public final class AssinadorXml {

    private static final String NFE_NS = "http://www.portalfiscal.inf.br/nfe";

    static {
        Init.init();
    }

    private AssinadorXml() {
    }

    public static void assinar(Document documentoNaoAssinado, byte[] pfxBytes, String senha) {
        assinarElemento(documentoNaoAssinado, pfxBytes, senha, "infNFe");
    }

    public static void assinarEvento(Document documentoNaoAssinado, byte[] pfxBytes, String senha) {
        assinarElemento(documentoNaoAssinado, pfxBytes, senha, "infEvento");
    }

    public static void assinarInutilizacao(Document documentoNaoAssinado, byte[] pfxBytes, String senha) {
        assinarElemento(documentoNaoAssinado, pfxBytes, senha, "infInut");
    }

    private static void assinarElemento(Document documentoNaoAssinado, byte[] pfxBytes, String senha, String nomeElemento) {
        KeyStore.PrivateKeyEntry chavePrivada = CertificadoA1.carregar(pfxBytes, senha);

        NodeList elementos = documentoNaoAssinado.getElementsByTagNameNS(NFE_NS, nomeElemento);
        Element elementoAssinado = (Element) elementos.item(0);
        String id = elementoAssinado.getAttribute("Id");
        // Sem DTD/schema, o DOM não reconhece "Id" como atributo do tipo ID por padrão;
        // isso é necessário para que o resolver de referência "#id" do Santuario funcione.
        elementoAssinado.setIdAttribute("Id", true);

        try {
            // O schema oficial bundled (xmldsig-core-schema_v1.01.xsd) fixa
            // SignatureMethod/DigestMethod em rsa-sha1/sha1 e restringe os
            // Transform aceitos a enveloped-signature + C14N puro (sem
            // "WithComments") — não são valores default, são <xsd:restriction>
            // fixas. SEFAZ exige RSA-SHA1 para a assinatura do DFe em si
            // (TLS é outro assunto, não afetado por esta escolha). Vale para
            // infNFe, infEvento e infInut igualmente.
            XMLSignature assinatura = new XMLSignature(
                documentoNaoAssinado, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1
            );

            Transforms transforms = new Transforms(documentoNaoAssinado);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_OMIT_COMMENTS);
            assinatura.addDocument("#" + id, transforms, org.apache.xml.security.algorithms.MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1);

            assinatura.addKeyInfo((X509Certificate) chavePrivada.getCertificate());
            assinatura.sign(chavePrivada.getPrivateKey());

            documentoNaoAssinado.getDocumentElement().appendChild(assinatura.getElement());
        } catch (Exception e) {
            // Falha de assinatura em si (fora da carga/senha do certificado,
            // já tratada em CertificadoA1.carregar) é tratada como o mesmo
            // tipo de falha técnica: o spec documenta apenas 3 tipos de
            // exceção técnica (CertificadoException/ValidacaoXsdException/
            // ComunicacaoException) e assinatura mal-sucedida é, na prática,
            // um problema de certificado/chave inutilizável.
            throw new CertificadoException("Falha ao assinar o XML (" + nomeElemento + ")", e);
        }
    }
}
