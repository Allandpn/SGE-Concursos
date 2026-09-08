package br.com.estudos.shared.parametro;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/** Leitura/edição dos 18 parâmetros já existentes (docs/SPRINT-15-AJUSTES.md §2). */
@Service
public class ParametroService {

    private final ParametroRepository parametroRepository;

    public ParametroService(ParametroRepository parametroRepository) {
        this.parametroRepository = parametroRepository;
    }

    @Transactional(readOnly = true)
    public List<Parametro> listar() {
        return parametroRepository.findAll();
    }

    /**
     * Validação única pras 18 chaves — número positivo, sem faixa por
     * chave (docs/SPRINT-15-AJUSTES.md §0, decisão explícita do usuário).
     * `Parametro` não é entidade de domínio (01_DOMINIO §3), então isto
     * não é regra D-xx — mesmo princípio de checagem eager que
     * `ErroService` já usa pra `descricao`/`causa`/`confianca`.
     */
    @Transactional
    public Parametro atualizar(String chave, AtualizarParametroRequest request) {
        var parametro = parametroRepository.findById(chave)
            .orElseThrow(() -> new NotFoundException("Não existe um parâmetro com esta chave.", "PARAMETRO_INEXISTENTE"));

        parametro.setValor(exigirNumeroPositivo(request.valor()));
        return parametro;
    }

    private String exigirNumeroPositivo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException("O valor é obrigatório.", "VALOR_INVALIDO", "valor");
        }
        double numero;
        try {
            numero = Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            throw new ValidationException("O valor precisa ser um número.", "VALOR_INVALIDO", "valor");
        }
        if (numero <= 0) {
            throw new ValidationException("O valor precisa ser positivo.", "VALOR_INVALIDO", "valor");
        }
        return valor;
    }
}
