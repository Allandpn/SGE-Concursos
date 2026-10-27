package br.com.estudos.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
class TransporteHttp implements TransportePush {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public int enviar(URI endpoint, Map<String, String> cabecalhos, byte[] corpo) throws IOException, InterruptedException {
        var pedido = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.ofByteArray(corpo));
        cabecalhos.forEach(pedido::header);
        return http.send(pedido.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
