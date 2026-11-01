package br.com.estudos.acesso;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;

/**
 * Os passkeys do titular, como a biblioteca de WebAuthn os pede (ADR-051). Um titular só: o nome de usuário e o
 * `userHandle` são constantes. Passkey removido não existe para entrar (D-111 o marca, D-18 o mantém).
 */
@Repository
public class PasskeyRepositorio implements CredentialRepository {

    static final String TITULAR = "titular";
    static final ByteArray HANDLE = new ByteArray("eagora-titular-1".getBytes(StandardCharsets.US_ASCII));

    public record Passkey(long id, String nome, OffsetDateTime criadoEm, OffsetDateTime ultimoUsoEm) {}

    private final JdbcTemplate jdbc;

    public PasskeyRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeAlgum() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM passkey WHERE removido_em IS NULL)", Boolean.class));
    }

    public List<Passkey> ativos() {
        return jdbc.query("SELECT id, nome, criado_em, ultimo_uso_em FROM passkey WHERE removido_em IS NULL ORDER BY criado_em",
            (rs, n) -> new Passkey(rs.getLong("id"), rs.getString("nome"),
                rs.getObject("criado_em", OffsetDateTime.class), rs.getObject("ultimo_uso_em", OffsetDateTime.class)));
    }

    public long gravar(ByteArray credencialId, ByteArray chavePublica, long contador, String nome, OffsetDateTime agora) {
        return jdbc.queryForObject("""
            INSERT INTO passkey (credencial_id, chave_publica, contador, nome, criado_em) VALUES (?, ?, ?, ?, ?) RETURNING id""",
            Long.class, credencialId.getBytes(), chavePublica.getBytes(), contador, nome, agora);
    }

    /** Depois de uma assinatura válida. O gatilho `trg_passkey_d107_contador` é a segunda barreira de D-107. */
    public Optional<Long> usado(ByteArray credencialId, long contador, OffsetDateTime agora) {
        var ids = jdbc.queryForList("""
            UPDATE passkey SET contador = ?, ultimo_uso_em = ? WHERE credencial_id = ? AND removido_em IS NULL RETURNING id""",
            Long.class, contador, agora, credencialId.getBytes());
        return ids.stream().findFirst();
    }

    /** Marca removido; o gatilho `trg_passkey_d111_ultimo` recusa o último. */
    public int remover(long id, OffsetDateTime agora) {
        return jdbc.update("UPDATE passkey SET removido_em = ? WHERE id = ? AND removido_em IS NULL", agora, id);
    }

    public int ativosAlemDe(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM passkey WHERE removido_em IS NULL AND id <> ?", Integer.class, id);
    }

    // --- CredentialRepository -------------------------------------------------------------------------------------

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String usuario) {
        if (!TITULAR.equals(usuario)) {
            return Set.of();
        }
        return new HashSet<>(jdbc.query("SELECT credencial_id FROM passkey WHERE removido_em IS NULL",
            (rs, n) -> PublicKeyCredentialDescriptor.builder().id(new ByteArray(rs.getBytes(1))).build()));
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String usuario) {
        return TITULAR.equals(usuario) ? Optional.of(HANDLE) : Optional.empty();
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray handle) {
        return HANDLE.equals(handle) ? Optional.of(TITULAR) : Optional.empty();
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credencialId, ByteArray handle) {
        if (!HANDLE.equals(handle)) {
            return Optional.empty();
        }
        return lookupAll(credencialId).stream().findFirst();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credencialId) {
        return new HashSet<>(jdbc.query(
            "SELECT credencial_id, chave_publica, contador FROM passkey WHERE credencial_id = ? AND removido_em IS NULL",
            (rs, n) -> RegisteredCredential.builder()
                .credentialId(new ByteArray(rs.getBytes("credencial_id")))
                .userHandle(HANDLE)
                .publicKeyCose(new ByteArray(rs.getBytes("chave_publica")))
                .signatureCount(rs.getLong("contador"))
                .build(),
            credencialId.getBytes()));
    }
}
