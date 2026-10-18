package br.com.estudos.prova;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.prova.ProvaInterna.DadosLeitura;
import br.com.estudos.prova.ProvaInterna.Estado;
import br.com.estudos.prova.ProvaInterna.FalhaRequest;
import br.com.estudos.prova.ProvaInterna.GabaritoRequest;
import br.com.estudos.prova.ProvaInterna.Julgamento;
import br.com.estudos.prova.ProvaInterna.LeituraRequest;
import br.com.estudos.prova.ProvaInterna.Pendente;
import br.com.estudos.prova.ProvaInterna.VereditosRequest;
import br.com.estudos.prova.ProvaInterna.VereditosResponse;

/**
 * Rotas da prova para o planejamento no mesmo servidor (ADR-044, ADR-046;
 * docs/SPRINT-24-PROVA-GABARITO.md §3). O token é checado antes, por TokenInternoFilter.
 * Nenhuma rota lista questões fora do contexto de uma prova (D-72).
 */
@RestController
@RequestMapping("/internal/provas")
@Tag(name = "Interno · prova", description = "Só para o planejamento no mesmo servidor; exige X-Internal-Token (ADR-044)")
public class ProvaInternoController {

    private final ProvaService provaService;

    public ProvaInternoController(ProvaService provaService) {
        this.provaService = provaService;
    }

    @GetMapping("/pendentes")
    @Operation(summary = "Provas que pedem trabalho", description = "LENDO (ler a prova, ou só o gabarito se jaLida) e JULGANDO.")
    public List<Pendente> pendentes() {
        return provaService.pendentes();
    }

    @GetMapping("/{id}/leitura")
    @Operation(summary = "O que o worker precisa para ler a prova", description = "Caminhos em entrada/ e os assuntos da cobertura do alvo, para a atribuição.")
    public DadosLeitura dadosLeitura(@PathVariable Long id) {
        return provaService.dadosLeitura(id);
    }

    @PostMapping("/{id}/leitura")
    @Operation(summary = "Entrega as questões lidas (e o gabarito, se houver)", description = "Sem gabarito, a prova espera (D-81); com gabarito, vai para a conferência. Gabarito com número de respostas diferente do de questões é recusado (D-82).")
    public Estado entregarLeitura(@PathVariable Long id, @RequestBody LeituraRequest pedido) {
        return provaService.entregarLeitura(id, pedido);
    }

    @PostMapping("/{id}/gabarito")
    @Operation(summary = "Entrega só o gabarito", description = "De uma prova já lida que esperava por ele, ou o definitivo que substitui o preliminar (D-84).")
    public Estado entregarGabarito(@PathVariable Long id, @RequestBody GabaritoRequest pedido) {
        return provaService.entregarGabarito(id, pedido);
    }

    @GetMapping("/{id}/julgamento")
    @Operation(summary = "As questões a julgar", description = "Só as que mudaram (D-79, D-84), com o assunto e seus segmentos ativos.")
    public Julgamento paraJulgar(@PathVariable Long id) {
        return provaService.paraJulgar(id);
    }

    @PostMapping("/{id}/vereditos")
    @Operation(summary = "Entrega um lote de vereditos", description = "Citação inválida rebaixa o item para NAO_RESPONDIVEL (D-68). Veredito é sempre linha nova (D-73).")
    public VereditosResponse registrarVereditos(@PathVariable Long id, @RequestBody VereditosRequest pedido) {
        return provaService.registrarVereditos(id, pedido);
    }

    @PostMapping("/{id}/falha")
    @Operation(summary = "A leitura ou o julgamento falhou")
    public Estado falhar(@PathVariable Long id, @RequestBody FalhaRequest pedido) {
        return provaService.falhar(id, pedido);
    }
}
