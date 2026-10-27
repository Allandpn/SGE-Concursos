package br.com.estudos.push;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.ValidationException;

/**
 * Avisos de processo concluído por Web Push (ADR-048, D-96): a chave VAPID, os aparelhos inscritos e o envio.
 * Quem decide <em>quando</em> avisar é {@link AvisoDeProcesso}; aqui só se entrega.
 */
@Service
public class PushService {

    private static final Logger LOG = LoggerFactory.getLogger(PushService.class);

    /** O endereço da inscrição vem do navegador: só se envia a serviços de push conhecidos (senão seria SSRF). */
    static final List<String> SERVICOS = List.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com",
        "notify.windows.com", "push.apple.com", "web.push.apple.com");

    public record Aviso(String titulo, String corpo, String url) {}

    public record Resultado(int enviados, int removidos, int falhas) {}

    private final JdbcTemplate jdbc;
    private final TransportePush transporte;
    private final tools.jackson.databind.json.JsonMapper json;

    public PushService(JdbcTemplate jdbc, TransportePush transporte, tools.jackson.databind.json.JsonMapper json) {
        this.jdbc = jdbc;
        this.transporte = transporte;
        this.json = json;
    }

    static boolean enderecoPermitido(String endpoint) {
        try {
            var uri = new URI(endpoint);
            var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            return "https".equals(uri.getScheme()) && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null
                && endpoint.length() < 1000 && SERVICOS.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** A chave pública (a privada nunca sai); gera o par na primeira vez. */
    @Transactional
    public String chavePublica() {
        var existente = jdbc.queryForList("SELECT chave_publica FROM push_config WHERE id = 1", String.class);
        if (!existente.isEmpty()) return existente.get(0);
        var par = PushCripto.gerarVapid();
        jdbc.update("INSERT INTO push_config (id, chave_publica, chave_privada) VALUES (1, ?, ?) ON CONFLICT (id) DO NOTHING", par.publica(), par.privada());
        return jdbc.queryForObject("SELECT chave_publica FROM push_config WHERE id = 1", String.class);
    }

    @Transactional
    public void inscrever(String endpoint, String p256dh, String auth) {
        if (endpoint == null || !enderecoPermitido(endpoint)) {
            throw new ValidationException("Endereço de push não aceito.", "ENDERECO_DE_PUSH_INVALIDO", "endpoint");
        }
        try {
            if (PushCripto.deB64(p256dh).length != 65 || PushCripto.deB64(auth).length != 16) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new ValidationException("Chaves da inscrição inválidas.", "CHAVES_DE_PUSH_INVALIDAS", "keys");
        }
        jdbc.update("""
            INSERT INTO push_inscricao (endpoint, p256dh, auth) VALUES (?, ?, ?)
            ON CONFLICT (endpoint) DO UPDATE SET p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth""", endpoint, p256dh, auth);
    }

    @Transactional
    public void remover(String endpoint) {
        jdbc.update("DELETE FROM push_inscricao WHERE endpoint = ?", endpoint);
    }

    /** Manda o aviso a todos os aparelhos; nunca lança: aviso é auxiliar e não pode desfazer o que o disparou. */
    public Resultado enviar(Aviso aviso) {
        int enviados = 0, removidos = 0, falhas = 0;
        try {
            var inscricoes = jdbc.queryForList("SELECT id, endpoint, p256dh, auth FROM push_inscricao ORDER BY id");
            if (inscricoes.isEmpty()) return new Resultado(0, 0, 0);
            var publica = chavePublica();   // gera o par se ainda não existe
            var privada = jdbc.queryForObject("SELECT chave_privada FROM push_config WHERE id = 1", String.class);
            var carga = json.writeValueAsString(Map.of("titulo", aviso.titulo(), "corpo", aviso.corpo(), "url", aviso.url()))
                .getBytes(StandardCharsets.UTF_8);
            var apagar = new ArrayList<Long>();
            for (var i : inscricoes) {
                var endpoint = (String) i.get("endpoint");
                try {
                    if (!enderecoPermitido(endpoint)) { apagar.add(((Number) i.get("id")).longValue()); removidos++; continue; }
                    var uri = URI.create(endpoint);
                    var audiencia = uri.getScheme() + "://" + uri.getHost();
                    var jwt = PushCripto.jwtVapid(privada, audiencia, "mailto:sge@localhost", Instant.now().plusSeconds(12 * 3600).getEpochSecond());
                    var cabecalhos = Map.of("Content-Encoding", "aes128gcm", "Content-Type", "application/octet-stream", "TTL", "86400",
                        "Urgency", "normal", "Authorization", "vapid t=" + jwt + ", k=" + publica);
                    int codigo = transporte.enviar(uri, cabecalhos, PushCripto.cifrar(carga, (String) i.get("p256dh"), (String) i.get("auth")));
                    if (codigo >= 200 && codigo < 300) {
                        enviados++;
                        jdbc.update("UPDATE push_inscricao SET ultimo_envio_em = now() WHERE id = ?", i.get("id"));
                    } else if (codigo == 404 || codigo == 410) {
                        apagar.add(((Number) i.get("id")).longValue());
                        removidos++;
                    } else {
                        falhas++;
                        LOG.warn("Serviço de push respondeu {} para uma inscrição.", codigo);
                    }
                } catch (Exception e) {
                    falhas++;
                    LOG.warn("Falha ao enviar aviso: {}", e.toString());
                }
            }
            apagar.forEach(id -> jdbc.update("DELETE FROM push_inscricao WHERE id = ?", id));
        } catch (RuntimeException e) {
            LOG.warn("Aviso não enviado: {}", e.toString());
            return new Resultado(enviados, removidos, falhas + 1);
        }
        return new Resultado(enviados, removidos, falhas);
    }
}
