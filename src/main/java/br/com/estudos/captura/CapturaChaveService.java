package br.com.estudos.captura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/** D-170 (ADR-063): a chave de envio da captura — uma por navegador, mostrada uma vez, guardada só como SHA-256, revogável. */
@Service
public class CapturaChaveService {

    public record Chave(long id, String nome, Instant criadaEm, Instant revogadaEm, Instant ultimoEnvioEm) {}

    public record Criada(long id, String nome, String chave) {}

    static final String PREFIXO = "eag_";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JdbcTemplate jdbc;

    public CapturaChaveService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Criada gerar(String nome) {
        var limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty() || limpo.length() > 120) {
            throw new ValidationException("Dê um nome ao navegador (até 120 letras).", "CHAVE_SEM_NOME", "nome");
        }
        var bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        var chave = PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var id = jdbc.queryForObject("INSERT INTO captura_chave (nome, resumo) VALUES (?, ?) RETURNING id", Long.class, limpo, resumo(chave));
        return new Criada(id, limpo, chave);
    }

    @Transactional(readOnly = true)
    public List<Chave> listar() {
        return jdbc.query("SELECT id, nome, criada_em, revogada_em, ultimo_envio_em FROM captura_chave ORDER BY revogada_em NULLS FIRST, id DESC",
            (rs, n) -> new Chave(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()));
    }

    @Transactional
    public void revogar(long id) {
        if (jdbc.update("UPDATE captura_chave SET revogada_em = coalesce(revogada_em, now()) WHERE id = ?", id) == 0) {
            throw new NotFoundException("Chave não encontrada.", "CHAVE_INEXISTENTE");
        }
    }

    /** A chave do pedido, se vale; marca o último envio. */
    @Transactional
    public OptionalLong autenticar(String chave) {
        if (chave == null || !chave.startsWith(PREFIXO) || chave.length() > 100) return OptionalLong.empty();
        var ids = jdbc.queryForList("UPDATE captura_chave SET ultimo_envio_em = now() WHERE resumo = ? AND revogada_em IS NULL RETURNING id",
            Long.class, resumo(chave));
        return ids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ids.get(0));
    }

    static String resumo(String chave) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chave.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
