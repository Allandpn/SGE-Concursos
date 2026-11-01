package br.com.estudos.acesso;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yubico.webauthn.RegistrationResult;

import br.com.estudos.acesso.FreioDeTentativas.Tipo;
import br.com.estudos.acesso.SessaoAcessoService.Atual;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.MuitasTentativasException;
import br.com.estudos.shared.exception.NaoAutorizadoException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ProibidoException;
import jakarta.servlet.http.HttpServletRequest;

/** O acesso (01_DOMINIO §3.16, D-106 a D-112; docs/SPRINT-30-LOGIN.md §3). */
@Service
public class AcessoService {

    private static final Logger LOG = LoggerFactory.getLogger(AcessoService.class);
    /** A porta nunca diz o que existe nem por que falhou (J-9). */
    static final String NAO_FOI_POSSIVEL = "Não foi possível entrar.";

    public record Estado(boolean logado, boolean primeiroAcesso) {}

    /** Resultado de entrar: o valor do cookie (só existe aqui) e, no primeiro cadastro, os códigos (mostrados uma vez). */
    public record Entrada(String token, List<String> codigos) {}

    public record PasskeyVisivel(long id, String nome, OffsetDateTime criadoEm, OffsetDateTime ultimoUsoEm, boolean esteAparelho) {}

    private final PasskeyRepositorio passkeys;
    private final WebAuthn webAuthn;
    private final SessaoAcessoService sessoes;
    private final CodigosDeRecuperacao codigos;
    private final FreioDeTentativas freio;
    private final JdbcTemplate jdbc;
    private final Clock relogio;
    private final long atrasoFalhaMs;

    public AcessoService(PasskeyRepositorio passkeys, WebAuthn webAuthn, SessaoAcessoService sessoes, CodigosDeRecuperacao codigos,
                         FreioDeTentativas freio, JdbcTemplate jdbc, Clock relogio,
                         @Value("${estudos.acesso.atraso-falha-ms:1000}") long atrasoFalhaMs) {
        this.passkeys = passkeys;
        this.webAuthn = webAuthn;
        this.sessoes = sessoes;
        this.codigos = codigos;
        this.freio = freio;
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.atrasoFalhaMs = atrasoFalhaMs;
    }

    /** D-108: `primeiroAcesso` só é verdadeiro pela tailnet e sem passkey; de fora, a porta não diz se algo existe. */
    public Estado estado(HttpServletRequest pedido, Optional<Atual> sessao) {
        return new Estado(sessao.isPresent(), OrigemDoPedido.tailnet(pedido) && !passkeys.existeAlgum());
    }

    // --- O primeiro passkey (D-108) -------------------------------------------------------------------------------

    public WebAuthn.Opcoes iniciarPrimeiro(HttpServletRequest pedido) {
        exigirPrimeiroAberto(pedido);
        return webAuthn.iniciarCadastro(pedido);
    }

    @Transactional
    public Entrada concluirPrimeiro(HttpServletRequest pedido, String pedidoId, String credencial, String nome) {
        // Dois cadastros ao mesmo tempo: o segundo espera e encontra o primeiro já gravado.
        jdbc.execute("SELECT pg_advisory_xact_lock(106108)");
        exigirPrimeiroAberto(pedido);
        var resultado = webAuthn.concluirCadastro(pedido, pedidoId, credencial);
        var id = gravar(resultado, nome);
        var novos = codigos.gerar(agora());
        LOG.info("acesso: primeiro passkey cadastrado (id {})", id);
        return new Entrada(sessoes.abrir(id, aparelho(pedido)), novos);
    }

    private void exigirPrimeiroAberto(HttpServletRequest pedido) {
        if (!OrigemDoPedido.tailnet(pedido) || passkeys.existeAlgum()) {
            throw new ProibidoException("O primeiro acesso é feito pela rede privada, e só enquanto não há passkey.", "PRIMEIRO_ACESSO_FECHADO");
        }
    }

    // --- Entrar (D-107, D-112) --------------------------------------------------------------------------------------

    public WebAuthn.Opcoes iniciarEntrada(HttpServletRequest pedido) {
        exigirSemBloqueio(Tipo.PASSKEY, pedido);
        return webAuthn.iniciarEntrada(pedido);
    }

    @Transactional
    public Entrada entrar(HttpServletRequest pedido, String pedidoId, String credencial) {
        exigirSemBloqueio(Tipo.PASSKEY, pedido);
        var resultado = webAuthn.concluirEntrada(pedido, pedidoId, credencial);
        Optional<Long> passkeyId = Optional.empty();
        if (resultado.isPresent()) {
            try {
                passkeyId = passkeys.usado(resultado.get().getCredential().getCredentialId(), resultado.get().getSignatureCount(), agora());
            } catch (DataIntegrityViolationException e) {
                // trg_passkey_d107_contador: a segunda barreira de D-107 recusou.
                LOG.warn("acesso: contador do passkey não avançou; assinatura recusada");
            }
        }
        if (passkeyId.isEmpty()) {
            falhou(Tipo.PASSKEY, pedido);
        }
        freio.deuCerto(Tipo.PASSKEY, OrigemDoPedido.cliente(pedido));
        return new Entrada(sessoes.abrir(passkeyId.get(), aparelho(pedido)), List.of());
    }

    /** D-110: o código consumido abre uma sessão; o aparelho cadastra o passkey dele em seguida (D-111). */
    @Transactional
    public Entrada recuperar(HttpServletRequest pedido, String codigo) {
        exigirSemBloqueio(Tipo.CODIGO, pedido);
        if (!codigos.usar(codigo, agora())) {
            falhou(Tipo.CODIGO, pedido);
        }
        freio.deuCerto(Tipo.CODIGO, OrigemDoPedido.cliente(pedido));
        LOG.info("acesso: entrada por código de recuperação; restam {}", codigos.restantes());
        return new Entrada(sessoes.abrir(null, aparelho(pedido)), List.of());
    }

    private void exigirSemBloqueio(Tipo tipo, HttpServletRequest pedido) {
        var ate = freio.bloqueio(tipo, OrigemDoPedido.cliente(pedido), OrigemDoPedido.publica(pedido));
        if (ate.isPresent()) {
            throw new MuitasTentativasException("Muitas tentativas. Tente de novo mais tarde.", "MUITAS_TENTATIVAS", ate.get());
        }
    }

    private void falhou(Tipo tipo, HttpServletRequest pedido) {
        freio.falhou(tipo, OrigemDoPedido.cliente(pedido), OrigemDoPedido.publica(pedido));
        if (atrasoFalhaMs > 0) {
            try {
                Thread.sleep(atrasoFalhaMs); // D-112: falhar devagar
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new NaoAutorizadoException(NAO_FOI_POSSIVEL, "NAO_FOI_POSSIVEL_ENTRAR");
    }

    // --- Dentro (exigem sessão; o filtro garante) -------------------------------------------------------------------

    public WebAuthn.Opcoes iniciarOutroPasskey(HttpServletRequest pedido) {
        return webAuthn.iniciarCadastro(pedido);
    }

    @Transactional
    public long concluirOutroPasskey(HttpServletRequest pedido, Atual sessao, String pedidoId, String credencial, String nome) {
        var id = gravar(webAuthn.concluirCadastro(pedido, pedidoId, credencial), nome);
        if (sessao.passkeyId() == null) {
            sessoes.ligarAoPasskey(sessao.id(), id);
        }
        LOG.info("acesso: passkey cadastrado (id {})", id);
        return id;
    }

    public List<PasskeyVisivel> passkeys(Atual sessao) {
        return passkeys.ativos().stream()
            .map(p -> new PasskeyVisivel(p.id(), p.nome(), p.criadoEm(), p.ultimoUsoEm(), sessao.passkeyId() != null && sessao.passkeyId() == p.id()))
            .toList();
    }

    /** D-111: o último não se remove (aqui e no gatilho `trg_passkey_d111_ultimo`). */
    @Transactional
    public void removerPasskey(long id) {
        if (passkeys.ativosAlemDe(id) == 0) {
            throw new ConflictException("O último passkey não se remove.", "ULTIMO_PASSKEY");
        }
        if (passkeys.remover(id, agora()) == 0) {
            throw new NotFoundException("Passkey não encontrado.", "PASSKEY_NAO_ENCONTRADO");
        }
    }

    public void sair(Atual sessao) {
        sessoes.encerrar(sessao.id());
    }

    public void sairDeTodos() {
        sessoes.encerrarTodas();
    }

    public int codigosRestantes() {
        return codigos.restantes();
    }

    @Transactional
    public List<String> gerarCodigos() {
        return codigos.gerar(agora());
    }

    private long gravar(RegistrationResult resultado, String nome) {
        var limpo = nome == null || nome.isBlank() ? "Aparelho" : nome.strip();
        return passkeys.gravar(resultado.getKeyId().getId(), resultado.getPublicKeyCose(), resultado.getSignatureCount(),
            limpo.length() > 80 ? limpo.substring(0, 80) : limpo, agora());
    }

    private static String aparelho(HttpServletRequest pedido) {
        return pedido.getHeader("User-Agent");
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    }
}
