package br.com.estudos.push;

import java.net.URI;
import java.util.Map;

/** Faz a requisição ao serviço de push do navegador e devolve o código HTTP; separado para os testes não saírem para a rede. */
public interface TransportePush {

    int enviar(URI endpoint, Map<String, String> cabecalhos, byte[] corpo) throws java.io.IOException, InterruptedException;
}
