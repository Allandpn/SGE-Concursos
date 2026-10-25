package br.com.estudos.conferencia;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.conferencia.EditalInterno.DadosEdital;
import br.com.estudos.conferencia.EditalInterno.FalhaRequest;
import br.com.estudos.conferencia.EditalInterno.Pendente;
import br.com.estudos.conferencia.EditalInterno.ResumoEntrega;
import br.com.estudos.edital.AnaliseEditalService;

/**
 * Rotas do edital para o planejamento no mesmo servidor (ADR-047; docs/SPRINT-25-CONFERENCIA-EDITAL.md §4). O token é
 * checado antes, por TokenInternoFilter.
 */
@RestController
@RequestMapping("/internal/editais")
@Tag(name = "Interno · edital", description = "Só para o planejamento no mesmo servidor; exige X-Internal-Token (ADR-044)")
public class EditalInternoController {

    private final EntregaEditalService entrega;
    private final AnaliseEditalService analises;
    private final EditalMaterialService material;

    public EditalInternoController(EntregaEditalService entrega, AnaliseEditalService analises, EditalMaterialService material) {
        this.entrega = entrega;
        this.analises = analises;
        this.material = material;
    }

    @GetMapping("/pendentes")
    @Operation(summary = "Editais que esperam o planejamento", description = "Análises com o cargo escolhido, em SEPARANDO_ASSUNTOS.")
    public List<Pendente> pendentes() {
        return entrega.pendentes();
    }

    @GetMapping("/material/pendentes")
    @Operation(summary = "Assuntos de uma conferência concluída que esperam material", description = "No máximo um edital por vez, com as partes que a pessoa conferiu (os nós dela, não os da IA) e se o assunto já tem segmentos.")
    public List<EditalMaterialService.MaterialPendente> materialPendente() {
        return material.pendentes();
    }

    @PostMapping("/{id}/material")
    @Operation(summary = "Entrega o material de cada assunto", description = "Situação (COMPLETO, PARCIAL, SEM_MATERIAL, RECUSADO), tempo, lacunas por parte e o PDF de consulta. Idempotente; assunto de outra análise: 404.")
    public java.util.Map<String, Object> entregarMaterial(@PathVariable Long id, @RequestBody tools.jackson.databind.JsonNode corpo) {
        return java.util.Map.of("atualizados", material.entregar(id, corpo));
    }

    @GetMapping("/{id}/dados")
    @Operation(summary = "O que o planejamento precisa para ler o edital", description = "O PDF no volume editais, o cargo, a árvore vigente e as decisões da pessoa que valem antes da IA (D-87).")
    public DadosEdital dados(@PathVariable Long id) {
        return entrega.dados(id);
    }

    @PostMapping("/{id}/revisao")
    @Operation(summary = "Entrega a leitura do edital (edital-revisao/1)", description = "Idempotente pelo hash do corpo (D-89). Contrato desconhecido: 422 CONTRATO_DESCONHECIDO. Nó fora da árvore: 422 NO_INEXISTENTE. Árvore não carregada: 409 ARVORE_NAO_CARREGADA.")
    public ResumoEntrega entregar(@PathVariable Long id, @RequestBody String corpo) {
        return entrega.entregar(id, corpo);
    }

    @PostMapping("/{id}/falha")
    @Operation(summary = "A leitura do edital falhou", description = "A análise vira FALHOU com a etapa (EXTRACAO, ORGANIZACAO, QUEBRA, LIGACAO) e a mensagem.")
    public void falha(@PathVariable Long id, @RequestBody FalhaRequest pedido) {
        analises.falhaDoPlanejamento(id, pedido.etapa(), pedido.disciplina(), pedido.mensagem());
    }
}
