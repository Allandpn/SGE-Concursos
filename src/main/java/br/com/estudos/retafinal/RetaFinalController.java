package br.com.estudos.retafinal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** A reta final (docs/SPRINT-21-RETA-FINAL.md §2; D-140, D-141, D-143; ADR-056). */
@RestController
@Tag(name = "Reta final", description = "Sugerida perto da prova, só entra por gesto da pessoa; termina sozinha depois da prova")
public class RetaFinalController {

    private final RetaFinalService retaFinal;

    public RetaFinalController(RetaFinalService retaFinal) {
        this.retaFinal = retaFinal;
    }

    @GetMapping("/api/reta-final")
    @Operation(summary = "A reta final ativa e, sem ela, a sugestão de entrar (D-140, D-141)")
    public RetaFinalService.Situacao situacao() {
        return retaFinal.situacao();
    }

    @PostMapping("/api/concursos/{id}/reta-final")
    @Operation(summary = "Entra na reta final do concurso-alvo e antecipa a manutenção de peso alto (D-140, D-143)",
        description = "409 SEM_DATA_DE_PROVA sem data futura; 409 NAO_E_O_ALVO se o concurso não é o alvo.")
    public RetaFinalService.Entrada entrar(@PathVariable long id) {
        return retaFinal.entrar(id);
    }

    @PostMapping("/api/concursos/{id}/reta-final/sair")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sai da reta final; as antecipações ficam (D-141)")
    public void sair(@PathVariable long id) {
        retaFinal.sair(id);
    }

    @PostMapping("/api/concursos/{id}/reta-final/dispensar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "\"Agora não\": cala a sugestão da reta final para este concurso (D-140)")
    public void dispensar(@PathVariable long id) {
        retaFinal.dispensar(id);
    }
}
