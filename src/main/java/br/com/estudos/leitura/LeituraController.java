package br.com.estudos.leitura;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O leitor (J-10; docs/SPRINT-31-LEITOR.md §3; D-114 a D-121, ADR-052). */
@RestController
@Tag(name = "Leitor", description = "O bloco no leitor do app, as marcações e a última página (D-114 a D-121)")
public class LeituraController {

    public record Pagina(Integer pagina) {}

    private final LeituraService leitura;
    private final MarcacaoService marcacoes;
    private final PontosAltosService pontosAltos;

    public LeituraController(LeituraService leitura, MarcacaoService marcacoes, PontosAltosService pontosAltos) {
        this.leitura = leitura;
        this.marcacoes = marcacoes;
        this.pontosAltos = pontosAltos;
    }

    @GetMapping("/api/segmentos/{id}/leitura")
    @Operation(summary = "Abre um bloco no leitor: assunto, posição entre os blocos, última página e marcações vigentes")
    public LeituraService.Leitura abrir(@PathVariable long id) {
        return leitura.abrir(id);
    }

    @PutMapping("/api/segmentos/{id}/leitura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Guarda a última página aberta deste bloco (D-121)")
    public void guardarPagina(@PathVariable long id, @RequestBody Pagina corpo) {
        leitura.guardarPagina(id, corpo.pagina());
    }

    @PostMapping("/api/marcacoes/mudancas")
    @Operation(summary = "Aplica um lote de mudanças de marcação", description = "Idempotente por mudancaId (D-118). Cada mudança vale sozinha: "
        + "uma recusada não derruba as outras. MARCACAO_DESCONHECIDA: a criação ainda não chegou; MUDANCA_INVALIDA: nunca vai valer.")
    public MarcacaoService.Resultado mudancas(@RequestBody List<MarcacaoService.Mudanca> lote) {
        return marcacoes.aplicar(lote);
    }

    @GetMapping("/api/segmentos/{id}/marcacoes/historico")
    @Operation(summary = "Marcações removidas e todas as versões, só para consulta (D-117)")
    public MarcacaoService.Historico historico(@PathVariable long id) {
        return marcacoes.historico(id);
    }

    @GetMapping("/api/assuntos/{id}/pontos-altos")
    @Operation(summary = "As marcações vigentes do assunto, para a recuperação depois de conferir (D-172)",
        description = "Destaques (com a nota de trecho) e notas de página, por bloco, página e altura. Bloco arquivado fica de fora. "
            + "A tela só pede depois do toque em Conferir.")
    public List<PontosAltosService.PontoAlto> pontosAltos(@PathVariable long id) {
        return pontosAltos.doAssunto(id);
    }
}
