package net.accellog.sefaz4j.nfse.xml;

import net.accellog.sefaz4j.nfse.chave.DpsIdCalculator;
import net.accellog.sefaz4j.nfse.model.ObjectFactory;
import net.accellog.sefaz4j.nfse.model.TCDPS;
import net.accellog.sefaz4j.nfse.model.TCInfDPS;
import net.accellog.sefaz4j.nfse.model.TCInfoPrestador;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Monta o DOM {@link Document} de uma DPS a partir de um {@link TCDPS} já
 * populado, injetando o atributo {@code Id} de {@code infDPS} quando ainda
 * ausente.
 *
 * <p>Espelha {@link net.accellog.sefaz4j.cte.xml.CTeXmlBuilder}. Não assina o
 * documento nem valida contra o XSD — apenas serializa o objeto JAXB para
 * DOM.</p>
 */
public final class DpsXmlBuilder {

    private DpsXmlBuilder() {
    }

    public static Document marcarIdEMontarDocumento(TCDPS dps) throws Exception {
        injetarIdSeAusente(dps);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document documento = dbf.newDocumentBuilder().newDocument();

        JAXBContext contexto = JAXBContext.newInstance(TCDPS.class);
        Marshaller marshaller = contexto.createMarshaller();
        ObjectFactory fabrica = new ObjectFactory();
        JAXBElement<TCDPS> elementoRaiz = fabrica.createDPS(dps);
        marshaller.marshal(elementoRaiz, documento);

        return documento;
    }

    private static void injetarIdSeAusente(TCDPS dps) {
        TCInfDPS infDPS = dps.getInfDPS();
        String id = infDPS.getId();
        if (id != null && !id.isEmpty()) {
            return;
        }

        TCInfoPrestador prest = infDPS.getPrest();
        String tipoInscricaoFederal;
        String inscricaoFederal;
        // Mapeamento do código numérico do tipo de inscrição federal (CNPJ=2, CPF=1, NIF=3,
        // cNaoNIF=9) e a convenção de preenchimento com zeros à esquerda até 14 dígitos NÃO
        // foram confirmados contra nenhum manual oficial do NFS-e nem contra o ACBr — é um
        // placeholder de melhor esforço, isolado a este método, pendente de verificação futura.
        if (prest.getCNPJ() != null) {
            tipoInscricaoFederal = "2";
            inscricaoFederal = prest.getCNPJ();
        } else if (prest.getCPF() != null) {
            tipoInscricaoFederal = "1";
            inscricaoFederal = padEsquerda14(prest.getCPF());
        } else if (prest.getNIF() != null) {
            tipoInscricaoFederal = "3";
            inscricaoFederal = padEsquerda14(prest.getNIF());
        } else {
            tipoInscricaoFederal = "9";
            inscricaoFederal = padEsquerda14(prest.getCNaoNIF());
        }

        String novoId = DpsIdCalculator.calcular(
            infDPS.getCLocEmi(),
            tipoInscricaoFederal,
            inscricaoFederal,
            infDPS.getSerie(),
            infDPS.getNDPS()
        );
        infDPS.setId(novoId);
    }

    private static String padEsquerda14(String valor) {
        StringBuilder sb = new StringBuilder();
        for (int i = valor.length(); i < 14; i++) {
            sb.append('0');
        }
        sb.append(valor);
        return sb.toString();
    }
}
