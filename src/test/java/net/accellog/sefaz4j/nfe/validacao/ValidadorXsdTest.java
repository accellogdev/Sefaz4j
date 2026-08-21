package net.accellog.sefaz4j.nfe.validacao;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class ValidadorXsdTest {

    @Test
    public void aceitaXmlObviamenteInvalidoDeveFalhar() {
        String xmlSemCamposObrigatorios = "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">" +
            "<infNFe Id=\"NFe35250812345678000195550010000001231123456789\" versao=\"4.00\">" +
            "</infNFe></NFe>";

        try {
            ValidadorXsd.validar(xmlSemCamposObrigatorios);
            fail("Deveria ter lançado ValidacaoXsdException para XML sem os grupos obrigatórios (ide, emit, det, total...)");
        } catch (ValidacaoXsdException e) {
            assertFalse("A lista de violações não deve vir vazia", e.getViolacoes().isEmpty());
        }
    }
}
