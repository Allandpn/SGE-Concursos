package br.com.estudos.conferencia;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.conferencia.ConferenciaPedidos.Comentario;
import br.com.estudos.conferencia.ConferenciaPedidos.Dividir;
import br.com.estudos.conferencia.ConferenciaPedidos.EditarDecisao;
import br.com.estudos.conferencia.ConferenciaPedidos.Juntar;
import br.com.estudos.conferencia.ConferenciaPedidos.Motivo;
import br.com.estudos.conferencia.ConferenciaPedidos.Nos;
import br.com.estudos.conferencia.ConferenciaPedidos.Peso;
import br.com.estudos.conferencia.ConferenciaPedidos.ResolverTrecho;
import br.com.estudos.conferencia.ConferenciaPedidos.TextoDaParte;
import br.com.estudos.conferencia.ConferenciaPedidos.TextoDoAssunto;
import br.com.estudos.conferencia.ConferenciaResponses.AceitoSozinho;
import br.com.estudos.conferencia.ConferenciaResponses.Assunto;
import br.com.estudos.conferencia.ConferenciaResponses.DecisaoResponse;
import br.com.estudos.conferencia.ConferenciaResponses.Disciplina;
import br.com.estudos.conferencia.ConferenciaResponses.ItemDaFila;
import br.com.estudos.conferencia.ConferenciaResponses.Resumo;

/** A tela de conferência do edital (docs/SPRINT-25-CONFERENCIA-EDITAL.md §5). */
@RestController
@RequestMapping("/api")
@Tag(name = "Conferência do edital", description = "A IA propõe, a regra confere, a pessoa resolve (01_DOMINIO §3.12, D-86 a D-92)")
public class ConferenciaController {

    private static final String BASE = "/editais/analises/{id}";

    private final ConferenciaService service;
    private final ConclusaoConferenciaService conclusao;
    private final DecisaoService decisoes;
    private final MaterialDaConferencia materialDaConferencia;

    public ConferenciaController(ConferenciaService service, ConclusaoConferenciaService conclusao, DecisaoService decisoes,
                                 MaterialDaConferencia materialDaConferencia) {
        this.service = service;
        this.conclusao = conclusao;
        this.decisoes = decisoes;
        this.materialDaConferencia = materialDaConferencia;
    }

    @GetMapping(BASE + "/conferencia")
    @Operation(summary = "O cabeçalho da conferência", description = "As contagens, a execução e se já dá para concluir.")
    public Resumo resumo(@PathVariable Long id) {
        return service.resumo(id);
    }

    @GetMapping(BASE + "/conferencia/disciplinas")
    @Operation(summary = "O edital por disciplina", description = "Disciplinas, assuntos e partes, com a ligação e o estado de cada parte.")
    public List<Disciplina> disciplinas(@PathVariable Long id) {
        return service.disciplinas(id);
    }

    @GetMapping(BASE + "/conferencia/fila")
    @Operation(summary = "A fila de revisão", description = "Só o que pede decisão: bloqueantes primeiro, depois a gravidade e a ordem do edital.")
    public List<ItemDaFila> fila(@PathVariable Long id) {
        return service.fila(id);
    }

    @GetMapping(BASE + "/conferencia/aceitos-sozinhos")
    @Operation(summary = "O que entrou aceito sem pedir a pessoa", description = "Partes com nó que a regra deu como seguras (exato, ou mais amplo com poucos subitens), cada uma com o motivo. Derivado, nunca gravado.")
    public List<AceitoSozinho> aceitosSozinhos(@PathVariable Long id) {
        return service.aceitosSozinhos(id);
    }

    @GetMapping("/assuntos/{assuntoId}/partes-do-edital")
    @Operation(summary = "As partes do edital de um assunto", description = "Com o nó de cada uma e o aviso que ainda pede atenção. Vazio se o assunto não nasceu de uma conferência.")
    public List<ConferenciaResponses.ParteDoAssunto> partesDoAssunto(@PathVariable Long assuntoId) {
        return service.partesDoAssunto(assuntoId);
    }

    @GetMapping("/assuntos/{assuntoId}/material-da-conferencia")
    @Operation(summary = "O que o planejamento entregou do material do assunto", description = "Situação do casamento, tempo estimado, lacunas por parte e o PDF \"Questões deste assunto\" (consulta, fora da escada, D-94). 204 se o assunto não nasceu de uma conferência ou o material ainda está a preparar.")
    public org.springframework.http.ResponseEntity<MaterialDaConferencia.Info> materialDaConferencia(@PathVariable Long assuntoId) {
        var info = materialDaConferencia.porAssunto(List.of(assuntoId)).get(assuntoId);
        return info == null || info.situacao() == null
            ? org.springframework.http.ResponseEntity.noContent().build() : org.springframework.http.ResponseEntity.ok(info);
    }

    @GetMapping("/concursos/{concursoId}/avisos-de-conferencia")
    @Operation(summary = "O que ainda pede atenção no edital do concurso", description = "Partes sem filtro de questões, vínculos largos e nós que mudaram (docs §9). Não bloqueia nada.")
    public ConferenciaResponses.AvisosDoConcurso avisos(@PathVariable Long concursoId) {
        return service.avisosDoConcurso(concursoId);
    }

    @PostMapping(BASE + "/conferencia/reprocessar")
    @Operation(summary = "Lê o edital de novo", description = "O planejamento refaz a leitura e a entrega se funde na conferência pela chave; nada que você decidiu muda (D-86, D-89). Só com a conferência aberta.")
    public void reprocessar(@PathVariable Long id) {
        service.reprocessar(id);
    }

    @PostMapping(BASE + "/conferencia/concluir")
    @Operation(summary = "Conclui a conferência", description = "Cria disciplinas, assuntos e concurso e ativa o concurso (D-62, D-92). Recusa com divergência que bloqueia (D-91).")
    public Map<String, Object> concluir(@PathVariable Long id) {
        return conclusao.concluir(id);
    }

    // ---------------------------------------------------------------- partes

    @PostMapping(BASE + "/partes/{parteId}/aceitar")
    @Operation(summary = "Aceita a ligação proposta")
    public Assunto aceitar(@PathVariable Long id, @PathVariable Long parteId, @RequestBody(required = false) ConferenciaPedidos.Escopo escopo) {
        return service.aceitar(id, parteId, escopo);
    }

    @PutMapping(BASE + "/partes/{parteId}/nos")
    @Operation(summary = "Troca os nós da parte", description = "Até 3, com a natureza (EXATO, MAIS_AMPLO, PARCIAL). Vira decisão.")
    public Assunto trocarNos(@PathVariable Long id, @PathVariable Long parteId, @RequestBody Nos pedido) {
        return service.trocarNos(id, parteId, pedido);
    }

    @PostMapping(BASE + "/partes/{parteId}/sem-no")
    @Operation(summary = "A árvore não tem", description = "A parte fica sem filtro de questões e aparece como lacuna; não bloqueia o estudo.")
    public Assunto semNo(@PathVariable Long id, @PathVariable Long parteId, @RequestBody(required = false) ConferenciaPedidos.Escopo escopo) {
        return service.semNo(id, parteId, escopo);
    }

    @PutMapping(BASE + "/partes/{parteId}")
    @Operation(summary = "Edita o texto, o prefixo herdado ou o contexto", description = "Cria a parte nova (a chave muda) e refaz a conferência por regra do assunto.")
    public Assunto editarParte(@PathVariable Long id, @PathVariable Long parteId, @RequestBody TextoDaParte pedido) {
        return service.editarParte(id, parteId, pedido);
    }

    @PostMapping(BASE + "/partes/{parteId}/dividir")
    @Operation(summary = "Divide a parte em duas ou mais")
    public Assunto dividir(@PathVariable Long id, @PathVariable Long parteId, @RequestBody Dividir pedido) {
        return service.dividir(id, parteId, pedido);
    }

    @PostMapping(BASE + "/partes/{parteId}/juntar")
    @Operation(summary = "Junta a parte com outra do mesmo assunto")
    public Assunto juntar(@PathVariable Long id, @PathVariable Long parteId, @RequestBody Juntar pedido) {
        return service.juntar(id, parteId, pedido);
    }

    @GetMapping(BASE + "/partes/{parteId}/escopo-todos")
    @Operation(summary = "O que valer para todos os editais mudaria", description = "Quantas partes de outros editais, ainda por conferir, receberiam esta ligação (D-87). A tela mostra antes de pedir a confirmação.")
    public ConferenciaResponses.EscopoTodos previaDeTodos(@PathVariable Long id, @PathVariable Long parteId,
                                                          @RequestParam(value = "nos", required = false) List<Long> nos,
                                                          @RequestParam(value = "natureza", required = false) String natureza) {
        return service.previaDeTodos(id, parteId, nos, natureza);
    }

    @PostMapping(BASE + "/partes/{parteId}/descartar")
    @Operation(summary = "Descarta a parte, com motivo")
    public Assunto descartar(@PathVariable Long id, @PathVariable Long parteId, @RequestBody Motivo pedido) {
        return service.descartar(id, parteId, pedido.motivo());
    }

    @PostMapping(BASE + "/partes/{parteId}/comentario")
    @Operation(summary = "Comentário livre na parte")
    public Assunto comentar(@PathVariable Long id, @PathVariable Long parteId, @RequestBody Comentario pedido) {
        return service.comentar(id, parteId, pedido.texto());
    }

    // ---------------------------------------------------------------- assuntos, disciplinas, trechos

    @PostMapping(BASE + "/assuntos/{assuntoId}/aceitar")
    @Operation(summary = "Aceita o assunto como está", description = "Para o assunto que a regra recusou ou alertou.")
    public Assunto aceitarAssunto(@PathVariable Long id, @PathVariable Long assuntoId) {
        return service.aceitarAssunto(id, assuntoId);
    }

    @PutMapping(BASE + "/assuntos/{assuntoId}")
    @Operation(summary = "Corrige o texto do assunto", description = "A chave e a conferência do assunto se refazem.")
    public Assunto editarAssunto(@PathVariable Long id, @PathVariable Long assuntoId, @RequestBody TextoDoAssunto pedido) {
        return service.editarAssunto(id, assuntoId, pedido.texto());
    }

    @PostMapping(BASE + "/disciplinas/{disciplinaId}/aceitar-exatos")
    @Operation(summary = "Aceita de uma vez as partes exatas da disciplina")
    public Map<String, Object> aceitarExatos(@PathVariable Long id, @PathVariable Long disciplinaId) {
        var aceitas = service.aceitarExatos(id, disciplinaId);
        return Map.of("aceitas", aceitas.size(), "partes", aceitas);
    }

    @PostMapping(BASE + "/disciplinas/{disciplinaId}/aceitar-amplos")
    @Operation(summary = "Aceita de uma vez os vínculos mais amplos ou parciais da disciplina", description = "É aceitar o nó largo de propósito: o atalho para as questões do QConcursos continua valendo. Uma decisão por parte.")
    public Map<String, Object> aceitarAmplos(@PathVariable Long id, @PathVariable Long disciplinaId) {
        var aceitas = service.aceitarAmplos(id, disciplinaId);
        return Map.of("aceitas", aceitas.size(), "partes", aceitas);
    }

    @PutMapping(BASE + "/disciplinas/{disciplinaId}/peso")
    @Operation(summary = "Troca o peso da disciplina", description = "D-62; sem isso vale o que D-61 calcula das questões do quadro de provas.")
    public void peso(@PathVariable Long id, @PathVariable Long disciplinaId, @RequestBody Peso pedido) {
        service.trocarPeso(id, disciplinaId, pedido.peso());
    }

    @PostMapping(BASE + "/trechos/{trechoId}/resolver")
    @Operation(summary = "Resolve um trecho do edital sem assunto", description = "DESCARTAR (com motivo) ou INCLUIR como assunto.")
    public void resolverTrecho(@PathVariable Long id, @PathVariable Long trechoId, @RequestBody ResolverTrecho pedido) {
        service.resolverTrecho(id, trechoId, pedido);
    }

    // ---------------------------------------------------------------- decisões

    @GetMapping("/decisoes")
    @Operation(summary = "As decisões da pessoa", description = "Valem antes da IA em qualquer reprocessamento (D-86).")
    public List<DecisaoResponse> listarDecisoes(@RequestParam(value = "ativas", defaultValue = "true") boolean ativas) {
        return decisoes.listar(ativas);
    }

    @PutMapping("/decisoes/{id}")
    @Operation(summary = "Edita uma decisão")
    public DecisaoResponse editarDecisao(@PathVariable long id, @RequestBody EditarDecisao pedido) {
        return decisoes.editar(id, pedido);
    }

    @DeleteMapping("/decisoes/{id}")
    @Operation(summary = "Revoga uma decisão", description = "Marca como inativa; nada é apagado (D-18).")
    public void revogarDecisao(@PathVariable long id) {
        decisoes.revogar(id);
    }
}
