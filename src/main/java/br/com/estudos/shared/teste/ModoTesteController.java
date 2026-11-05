package br.com.estudos.shared.teste;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.shared.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** O modo de teste (docs/SPRINT-41-MODO-DE-TESTE.md §2; ADR-061): entrar, sair, recomeçar e mover a data do teste. */
@RestController
@RequestMapping("/api/modo-teste")
@Tag(name = "Modo de teste", description = "Um schema com a cópia dos dados reais e a data que se avança; só no aparelho que entrou")
public class ModoTesteController {

    public record Situacao(boolean ativo, boolean preparado, LocalDate hoje, int deslocamentoDias, Instant copiadoEm) {}

    public record Dias(Integer dias) {}

    public record Data(LocalDate data) {}

    private final ModoTesteService servico;
    private final Clock clock;

    public ModoTesteController(ModoTesteService servico, Clock clock) {
        this.servico = servico;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Se este aparelho está no modo de teste, e o \"hoje\" que o app usa (o do teste, se estiver)")
    public Situacao situacao(HttpServletRequest pedido) {
        var e = servico.estado();
        return new Situacao(ModoTeste.ligado(), e.preparado(), LocalDate.now(clock), e.deslocamentoDias(), e.copiadoEm());
    }

    @PostMapping("/entrar")
    @Operation(summary = "Entra no modo de teste neste aparelho; na primeira vez, copia os dados reais")
    public ResponseEntity<Situacao> entrar() {
        var e = servico.entrar();
        return comCookie("1", 60L * 60 * 24 * 365, e);
    }

    @PostMapping("/sair")
    @Operation(summary = "Sai do modo de teste neste aparelho; o teste fica guardado")
    public ResponseEntity<Situacao> sair() {
        return comCookie("", 0, servico.estado());
    }

    @PostMapping("/recomecar")
    @Operation(summary = "Refaz a cópia dos dados reais e volta a data do teste para hoje")
    public Situacao recomecar() {
        servico.recomecar();
        var e = servico.estado();
        return new Situacao(true, true, LocalDate.now(Clock.system(clock.getZone())), e.deslocamentoDias(), e.copiadoEm());
    }

    @PostMapping("/avancar")
    @Operation(summary = "Avança a data do teste em N dias")
    public Situacao avancar(@RequestBody Dias corpo) {
        if (corpo == null || corpo.dias() == null || corpo.dias() < 1 || corpo.dias() > 366) {
            throw new ValidationException("Avance de 1 a 366 dias.", "DIAS_INVALIDOS", "dias");
        }
        return mover(servico.estado().deslocamentoDias() + corpo.dias());
    }

    @PostMapping("/data")
    @Operation(summary = "Leva a data do teste para um dia escolhido")
    public Situacao irPara(@RequestBody Data corpo) {
        if (corpo == null || corpo.data() == null) throw new ValidationException("Escolha a data.", "DATA_OBRIGATORIA", "data");
        var e = servico.irPara(corpo.data());
        return new Situacao(true, true, corpo.data(), e.deslocamentoDias(), e.copiadoEm());
    }

    @PostMapping("/hoje")
    @Operation(summary = "Volta a data do teste para hoje de verdade")
    public Situacao hojeDeVerdade() {
        return mover(0);
    }

    private Situacao mover(int deslocamento) {
        var e = servico.deslocar(deslocamento);
        return new Situacao(true, true, LocalDate.now(Clock.system(clock.getZone())).plusDays(deslocamento), e.deslocamentoDias(), e.copiadoEm());
    }

    private static ResponseEntity<Situacao> comCookie(String valor, long maxAge, ModoTesteService.Estado e) {
        var cookie = ResponseCookie.from(ModoTesteFiltro.COOKIE, valor).path("/").sameSite("Strict").secure(true).httpOnly(true).maxAge(maxAge).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new Situacao(maxAge > 0, e.preparado(), null, e.deslocamentoDias(), e.copiadoEm()));
    }
}
