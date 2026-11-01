package br.com.estudos.acesso;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.acesso.SessaoAcessoService.Atual;
import br.com.estudos.shared.exception.NaoAutorizadoException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

/** O login por passkey (docs/SPRINT-30-LOGIN.md §3; D-106 a D-112, ADR-051). */
@RestController
@RequestMapping("/api/acesso")
@Tag(name = "Acesso", description = "Login por passkey, sessão e códigos de recuperação (D-106 a D-112)")
public class AcessoController {

    public record PedidoNome(String nome) {}

    /** `credencial` é o PublicKeyCredential do navegador em JSON (ids e bytes em base64url). */
    public record Conclusao(String pedidoId, JsonNode credencial, String nome) {}

    public record Codigo(String codigo) {}

    public record Codigos(List<String> codigos) {}

    private final AcessoService acesso;

    public AcessoController(AcessoService acesso) {
        this.acesso = acesso;
    }

    @GetMapping("/estado")
    @Operation(summary = "Se este aparelho está dentro, e se o primeiro acesso está aberto (só pela tailnet, sem passkey)")
    public AcessoService.Estado estado(HttpServletRequest pedido) {
        return acesso.estado(pedido, SessaoAcessoFilter.sessao(pedido));
    }

    @PostMapping("/primeiro/opcoes")
    @Operation(summary = "Opções do cadastro do primeiro passkey", description = "403 PRIMEIRO_ACESSO_FECHADO fora da tailnet ou se já há passkey (D-108).")
    public ResponseEntity<String> primeiroOpcoes(HttpServletRequest pedido) {
        return opcoes(acesso.iniciarPrimeiro(pedido));
    }

    @PostMapping("/primeiro")
    @Operation(summary = "Cadastra o primeiro passkey, abre a sessão e devolve os 8 códigos de recuperação (mostrados uma vez)")
    public ResponseEntity<Codigos> primeiro(@RequestBody Conclusao corpo, HttpServletRequest pedido) {
        var entrada = acesso.concluirPrimeiro(pedido, corpo.pedidoId(), texto(corpo.credencial()), corpo.nome());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, SessaoAcessoFilter.cookie(entrada.token()).toString())
            .body(new Codigos(entrada.codigos()));
    }

    @PostMapping("/entrar/opcoes")
    @Operation(summary = "Opções para entrar com passkey (credencial descoberta pelo aparelho)")
    public ResponseEntity<String> entrarOpcoes(HttpServletRequest pedido) {
        return opcoes(acesso.iniciarEntrada(pedido));
    }

    @PostMapping("/entrar")
    @Operation(summary = "Entra com passkey", description = "401 NAO_FOI_POSSIVEL_ENTRAR para qualquer falha; 429 com `ate` no bloqueio (D-112).")
    public ResponseEntity<Void> entrar(@RequestBody Conclusao corpo, HttpServletRequest pedido) {
        var entrada = acesso.entrar(pedido, corpo.pedidoId(), texto(corpo.credencial()));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, SessaoAcessoFilter.cookie(entrada.token()).toString()).build();
    }

    @PostMapping("/recuperar")
    @Operation(summary = "Entra com um código de recuperação, que é consumido (D-110)")
    public ResponseEntity<Void> recuperar(@RequestBody Codigo corpo, HttpServletRequest pedido) {
        var entrada = acesso.recuperar(pedido, corpo.codigo());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, SessaoAcessoFilter.cookie(entrada.token()).toString()).build();
    }

    // --- Dentro -------------------------------------------------------------------------------------------------------

    @GetMapping("/passkeys")
    @Operation(summary = "Os passkeys ativos, com o último uso e qual é deste aparelho")
    public List<AcessoService.PasskeyVisivel> passkeys(HttpServletRequest pedido) {
        return acesso.passkeys(exigir(pedido));
    }

    @PostMapping("/passkeys/opcoes")
    @Operation(summary = "Opções para cadastrar outro passkey (exige sessão, D-111)")
    public ResponseEntity<String> passkeyOpcoes(HttpServletRequest pedido) {
        exigir(pedido);
        return opcoes(acesso.iniciarOutroPasskey(pedido));
    }

    @PostMapping("/passkeys")
    @Operation(summary = "Cadastra outro passkey")
    public Map<String, Long> cadastrarPasskey(@RequestBody Conclusao corpo, HttpServletRequest pedido) {
        return Map.of("id", acesso.concluirOutroPasskey(pedido, exigir(pedido), corpo.pedidoId(), texto(corpo.credencial()), corpo.nome()));
    }

    @DeleteMapping("/passkeys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um passkey", description = "409 ULTIMO_PASSKEY para o último (D-111). Nada se apaga: fica marcado.")
    public void removerPasskey(@PathVariable long id, HttpServletRequest pedido) {
        exigir(pedido);
        acesso.removerPasskey(id);
    }

    @PostMapping("/sair")
    @Operation(summary = "Encerra a sessão deste aparelho")
    public ResponseEntity<Void> sair(HttpServletRequest pedido) {
        acesso.sair(exigir(pedido));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, SessaoAcessoFilter.cookieApagado().toString()).build();
    }

    @PostMapping("/sair-de-todos")
    @Operation(summary = "Encerra todas as sessões, deste e dos outros aparelhos")
    public ResponseEntity<Void> sairDeTodos(HttpServletRequest pedido) {
        exigir(pedido);
        acesso.sairDeTodos();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, SessaoAcessoFilter.cookieApagado().toString()).build();
    }

    @GetMapping("/codigos")
    @Operation(summary = "Quantos códigos de recuperação restam")
    public Map<String, Integer> codigos(HttpServletRequest pedido) {
        exigir(pedido);
        return Map.of("restantes", acesso.codigosRestantes());
    }

    @PostMapping("/codigos")
    @Operation(summary = "Gera 8 códigos novos (mostrados uma vez) e invalida os anteriores (D-110)")
    public Codigos gerarCodigos(HttpServletRequest pedido) {
        exigir(pedido);
        return new Codigos(acesso.gerarCodigos());
    }

    private static Atual exigir(HttpServletRequest pedido) {
        return SessaoAcessoFilter.sessao(pedido)
            .orElseThrow(() -> new NaoAutorizadoException("Entre para continuar.", "SESSAO_AUSENTE"));
    }

    /** As opções já vêm em JSON da biblioteca; vão como estão, com o id do pedido ao lado. */
    private static ResponseEntity<String> opcoes(WebAuthn.Opcoes opcoes) {
        var corpo = "{\"pedidoId\":\"" + opcoes.pedidoId() + "\",\"opcoes\":" + opcoes.publicKeyJson() + "}";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/json").body(corpo);
    }

    private static String texto(JsonNode credencial) {
        return credencial == null ? "" : credencial.toString();
    }
}
