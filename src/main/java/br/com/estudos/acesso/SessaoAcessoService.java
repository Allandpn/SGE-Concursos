package br.com.estudos.acesso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A sessão de acesso (D-109, docs/SPRINT-30-LOGIN.md §4): 30 dias no mesmo aparelho, renovada ao usar. O banco guarda
 * só o SHA-256 do valor do cookie. Encerrar é marcar (D-18).
 */
@Service
public class SessaoAcessoService {

    public static final Duration DURACAO = Duration.ofDays(30);
    /** Renova no máximo uma vez por hora: uma escrita por hora, não por pedido. */
    static final Duration RENOVA_DEPOIS_DE = Duration.ofHours(1);

    /** O que o filtro sabe da sessão válida. `renovada` diz se o cookie precisa ser mandado de novo. */
    public record Atual(long id, Long passkeyId, boolean renovada) {}

    private final JdbcTemplate jdbc;
    private final Clock relogio;

    public SessaoAcessoService(JdbcTemplate jdbc, Clock relogio) {
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    /** Abre uma sessão e devolve o valor do cookie, que não é guardado em lugar nenhum. */
    @Transactional
    public String abrir(Long passkeyId, String aparelho) {
        var token = Segredo.tokenDeSessao();
        var agora = agora();
        jdbc.update("""
            INSERT INTO sessao_acesso (resumo, passkey_id, aparelho, criada_em, ultimo_uso_em, expira_em)
            VALUES (?, ?, ?, ?, ?, ?)""",
            Segredo.resumo(token), passkeyId, cortar(aparelho), agora, agora, agora.plus(DURACAO));
        return token;
    }

    /** A sessão do cookie, se válida (não expirada, não encerrada); renova se o último uso tem mais de uma hora. */
    @Transactional
    public Optional<Atual> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        var agora = agora();
        var linhas = jdbc.query("""
            SELECT id, passkey_id, ultimo_uso_em FROM sessao_acesso
            WHERE resumo = ? AND encerrada_em IS NULL AND expira_em > ?""",
            (rs, n) -> new Object[] {rs.getLong("id"), rs.getObject("passkey_id", Long.class), rs.getObject("ultimo_uso_em", OffsetDateTime.class)},
            Segredo.resumo(token), agora);
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        var linha = linhas.get(0);
        long id = (Long) linha[0];
        var ultimoUso = ((OffsetDateTime) linha[2]).toInstant();
        boolean renovar = !ultimoUso.plus(RENOVA_DEPOIS_DE).isAfter(agora.toInstant());
        if (renovar) {
            jdbc.update("UPDATE sessao_acesso SET ultimo_uso_em = ?, expira_em = ? WHERE id = ?", agora, agora.plus(DURACAO), id);
        }
        return Optional.of(new Atual(id, (Long) linha[1], renovar));
    }

    @Transactional
    public void encerrar(long id) {
        jdbc.update("UPDATE sessao_acesso SET encerrada_em = ? WHERE id = ? AND encerrada_em IS NULL", agora(), id);
    }

    @Transactional
    public int encerrarTodas() {
        return jdbc.update("UPDATE sessao_acesso SET encerrada_em = ? WHERE encerrada_em IS NULL", agora());
    }

    /** Uma sessão que entrou por código de recuperação passa a ser deste passkey, cadastrado em seguida. */
    @Transactional
    public void ligarAoPasskey(long sessaoId, long passkeyId) {
        jdbc.update("UPDATE sessao_acesso SET passkey_id = ? WHERE id = ? AND passkey_id IS NULL", passkeyId, sessaoId);
    }

    Instant agoraInstante() {
        return relogio.instant();
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    }

    private static String cortar(String texto) {
        return texto == null ? null : texto.length() <= 200 ? texto : texto.substring(0, 200);
    }
}
