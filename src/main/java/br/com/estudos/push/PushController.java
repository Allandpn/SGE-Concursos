package br.com.estudos.push;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.push.PushService.Aviso;
import br.com.estudos.push.PushService.Resultado;

/** Avisos de processo concluído (docs/SPRINT-27-AVISOS-DE-PROCESSO.md §3). */
@RestController
@RequestMapping("/api/push")
@Tag(name = "Avisos", description = "Web Push para os avisos de processo concluído (D-96, ADR-048)")
public class PushController {

    public record Chaves(String p256dh, String auth) {}

    public record Inscricao(String endpoint, Chaves keys) {}

    public record Endereco(String endpoint) {}

    public record AvisoDeFase(String endpoint, boolean avisa) {}

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    @GetMapping("/chave")
    @Operation(summary = "A chave pública VAPID", description = "Gera o par na primeira chamada; a chave privada nunca é devolvida.")
    public Map<String, String> chave() {
        return Map.of("chavePublica", push.chavePublica());
    }

    @PostMapping("/inscricoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inscreve este aparelho", description = "Idempotente pelo endereço. Recusa (422) endereço fora da lista de serviços de push.")
    public void inscrever(@RequestBody Inscricao corpo) {
        push.inscrever(corpo.endpoint(), corpo.keys() == null ? null : corpo.keys().p256dh(), corpo.keys() == null ? null : corpo.keys().auth());
    }

    @PutMapping("/inscricoes/fase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Liga ou desliga o aviso de fim de fase do Pomodoro neste aparelho", description = "Desligado por padrão (D-104). 404 se o aparelho não está inscrito.")
    public void avisarFase(@RequestBody AvisoDeFase corpo) {
        push.avisarFase(corpo.endpoint(), corpo.avisa());
    }

    @DeleteMapping("/inscricoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Tira este aparelho", description = "204 mesmo se não estava inscrito.")
    public void remover(@RequestBody Endereco corpo) {
        push.remover(corpo.endpoint());
    }

    @PostMapping("/teste-fase")
    @Operation(summary = "Manda um aviso de fim de fase de teste, só aos aparelhos que pediram")
    public Resultado testeFase() {
        return push.enviarDeFase(new Aviso("Pomodoro", "O foco terminou.", "/"));
    }

    @PostMapping("/teste")
    @Operation(summary = "Manda um aviso de teste a todos os aparelhos inscritos")
    public Resultado teste() {
        return push.enviar(new Aviso("Aviso de teste", "Se você leu isto, os avisos estão funcionando neste aparelho.", "/#/ajustes"));
    }
}
