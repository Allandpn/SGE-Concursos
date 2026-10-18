package br.com.estudos.prova;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.estudos.prova.ProvaPublica.AssuntoDaQuestaoRequest;
import br.com.estudos.prova.ProvaPublica.CorrecaoRequest;
import br.com.estudos.prova.ProvaPublica.Detalhe;
import br.com.estudos.prova.ProvaPublica.Reanalise;
import br.com.estudos.prova.ProvaPublica.Relatorio;
import br.com.estudos.prova.ProvaPublica.Resumo;
import br.com.estudos.shared.exception.NotFoundException;

/**
 * Verificar o material com uma prova (J-6; docs/SPRINT-24-PROVA-GABARITO.md §4). Nenhuma rota
 * lista, busca ou exporta questões fora do contexto de uma prova (D-72): toda rota de questão
 * tem a prova no caminho. A resolução (J-7) chega no item 24.4.
 */
@RestController
@RequestMapping("/api/provas")
@Tag(name = "Provas", description = "Verificar se o material sustenta a resposta certa de uma prova (D-67 a D-85)")
public class ProvaController {

    private final ProvaUsuarioService service;
    private final ProvaArquivos arquivos;

    public ProvaController(ProvaUsuarioService service, ProvaArquivos arquivos) {
        this.service = service;
        this.arquivos = arquivos;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia a prova e, se houver, o gabarito",
        description = "PDF da prova; gabarito em PDF (`gabarito`) ou texto (`gabaritoTexto`). 202 e a prova em LENDO; a mesma prova de novo devolve a existente (200). Sem concurso-alvo, 422.")
    public ResponseEntity<Resumo> enviar(
            @RequestParam("prova") MultipartFile prova,
            @RequestParam(value = "gabarito", required = false) MultipartFile gabarito,
            @RequestParam(value = "gabaritoTexto", required = false) String gabaritoTexto,
            @RequestParam(value = "tipo", required = false) String tipo) {
        var envio = service.enviar(prova, gabarito, gabaritoTexto, tipo);
        return ResponseEntity.status(envio.criada() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(envio.prova());
    }

    @PostMapping(value = "/{id}/gabarito", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Anexa o gabarito a uma prova que o esperava", description = "Só em AGUARDANDO_GABARITO; a prova volta a LENDO (D-81).")
    public Resumo anexarGabarito(
            @PathVariable Long id,
            @RequestParam(value = "gabarito", required = false) MultipartFile gabarito,
            @RequestParam(value = "gabaritoTexto", required = false) String gabaritoTexto) {
        return service.anexarGabarito(id, gabarito, gabaritoTexto);
    }

    @GetMapping
    @Operation(summary = "As provas do concurso-alvo", description = "Da mais recente para a mais antiga, com o andamento do julgamento.")
    public List<Resumo> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Estado e conferência de uma prova",
        description = "Questões lidas, não avaliáveis, fora do edital, gabarito casado, recorte, 3 questões de amostra e as atribuições duvidosas.")
    public Detalhe detalhe(@PathVariable Long id) {
        return service.detalhe(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Corrige nome, banca, ano ou tipo", description = "O tipo só muda antes de o gabarito valer.")
    public Resumo corrigir(@PathVariable Long id, @RequestBody CorrecaoRequest pedido) {
        return service.corrigir(id, pedido);
    }

    @PutMapping("/{id}/questoes/{numero}/assunto")
    @Operation(summary = "Corrige a atribuição de uma questão",
        description = "assuntoId da cobertura do alvo, ou nulo = fora do edital (D-74). Só em CONFERIR.")
    public ResponseEntity<Void> corrigirAssunto(@PathVariable Long id, @PathVariable Integer numero,
                                                @RequestBody AssuntoDaQuestaoRequest pedido) {
        service.corrigirAssunto(id, numero, pedido.assuntoId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirmar")
    @Operation(summary = "Confirma a conferência", description = "CONFERIR → JULGANDO: a partir daqui o worker gasta IA.")
    public Resumo confirmar(@PathVariable Long id) {
        return service.confirmar(id);
    }

    @PostMapping("/{id}/analisar-de-novo")
    @Operation(summary = "Analisar de novo (D-79)", description = "Julga só as questões dos assuntos cujo material mudou, ou cujo gabarito mudou. Se nada mudou, fica PRONTA.")
    public Reanalise analisarDeNovo(@PathVariable Long id) {
        return service.analisarDeNovo(id);
    }

    @PostMapping("/{id}/tentar-de-novo")
    @Operation(summary = "Tenta de novo uma prova que falhou", description = "FALHOU → a etapa em que falhou.")
    public Resumo tentarDeNovo(@PathVariable Long id) {
        return service.tentarDeNovo(id);
    }

    @GetMapping("/{id}/relatorio")
    @Operation(summary = "Relatório de cobertura (D-70, D-71)",
        description = "Sempre com denominador; o que sai dele vem contado à parte; nunca o enunciado.")
    public Relatorio relatorio(@PathVariable Long id) {
        return service.relatorio(id);
    }

    @GetMapping("/{id}/paginas/{n}")
    @Operation(summary = "A imagem da página de origem de uma questão (D-85)", description = "Do diretório da prova; nunca sai dele.")
    public ResponseEntity<Resource> pagina(@PathVariable Long id, @PathVariable int n) {
        var arquivo = arquivos.pagina(id, n)
            .orElseThrow(() -> new NotFoundException("Página não encontrada.", "PAGINA_INEXISTENTE"));
        var tipo = arquivo.getFileName().toString().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(tipo).body(new FileSystemResource(arquivo));
    }
}
