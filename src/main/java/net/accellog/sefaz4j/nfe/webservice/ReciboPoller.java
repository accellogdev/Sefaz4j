package net.accellog.sefaz4j.nfe.webservice;

import net.accellog.sefaz4j.webservice.RespostaSefaz;
import net.accellog.sefaz4j.webservice.RespostaSefazParser;
import net.accellog.sefaz4j.webservice.SefazHttpClient;

import java.time.Duration;
import java.util.function.Supplier;

public final class ReciboPoller {

    private ReciboPoller() {
    }

    public static RespostaSefaz aguardarProtocolo(Supplier<RespostaSefaz> consultarUmaVez, int maxTentativas, Duration intervalo) {
        RespostaSefaz ultimaResposta = null;
        for (int tentativa = 0; tentativa < maxTentativas; tentativa++) {
            ultimaResposta = consultarUmaVez.get();
            if (!"103".equals(ultimaResposta.getCStat())) {
                return ultimaResposta;
            }
            try {
                Thread.sleep(intervalo.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ultimaResposta;
            }
        }
        return ultimaResposta;
    }

    public static RespostaSefaz aguardarProtocolo(
        String nRec,
        int tpAmb,
        String urlRetAutorizacao,
        byte[] pfxBytes,
        String senha,
        int maxTentativas,
        Duration intervalo,
        Duration timeoutRequisicao
    ) {
        Supplier<RespostaSefaz> consultarUmaVez = () -> {
            String envelope = SoapEnvelopeBuilder.envelopeRetAutorizacao(nRec, tpAmb);
            String resposta = SefazHttpClient.postar(
                urlRetAutorizacao,
                "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRetAutorizacao4/nfeRetAutorizacaoLote\"",
                envelope,
                pfxBytes,
                senha,
                timeoutRequisicao
            );
            return RespostaSefazParser.parsear(resposta);
        };
        return aguardarProtocolo(consultarUmaVez, maxTentativas, intervalo);
    }
}
