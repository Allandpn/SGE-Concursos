package br.com.estudos.acesso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;

import br.com.estudos.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * O WebAuthn do login (ADR-051, docs/SPRINT-30-LOGIN.md §3.2 e §4), sobre `webauthn-server-core`.
 *
 * O `rpId` e a origem vêm do próprio pedido: o navegador só deixa o site `X` usar o `rpId` `X`, então espelhar o pedido
 * não abre nada; como defesa a mais, o host precisa casar com `estudos.acesso.hosts`. O pedido pendente (o desafio) fica
 * na memória por 5 minutos e vale uma vez.
 */
@Component
public class WebAuthn {

    static final Duration VALIDADE_DO_PEDIDO = Duration.ofMinutes(5);

    enum Tipo { CADASTRO, ENTRADA }

    /** O que vai ao navegador: o id do pedido e as opções no formato de `navigator.credentials` (com `publicKey`). */
    public record Opcoes(String pedidoId, String publicKeyJson) {}

    private record Pendente(Tipo tipo, String opcoesJson, String rpId, String origem, Instant expira) {}

    private final PasskeyRepositorio passkeys;
    private final Clock relogio;
    private final List<String> hosts;
    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();

    public WebAuthn(PasskeyRepositorio passkeys, Clock relogio, @Value("${estudos.acesso.hosts:*.ts.net,localhost}") String hosts) {
        this.passkeys = passkeys;
        this.relogio = relogio;
        this.hosts = Arrays.stream(hosts.split(",")).map(h -> h.trim().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).toList();
    }

    public Opcoes iniciarCadastro(HttpServletRequest pedido) {
        var rp = parte(pedido);
        var opcoes = rp.startRegistration(StartRegistrationOptions.builder()
            .user(UserIdentity.builder().name(PasskeyRepositorio.TITULAR).displayName("eAgora").id(PasskeyRepositorio.HANDLE).build())
            .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build())
            .timeout(VALIDADE_DO_PEDIDO.toMillis())
            .build());
        try {
            return guardar(Tipo.CADASTRO, opcoes.toJson(), opcoes.toCredentialsCreateJson(), pedido);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public RegistrationResult concluirCadastro(HttpServletRequest pedido, String pedidoId, String credencialJson) {
        var pendente = retirar(pedidoId, Tipo.CADASTRO, pedido);
        try {
            var opcoes = PublicKeyCredentialCreationOptions.fromJson(pendente.opcoesJson());
            var resposta = PublicKeyCredential.parseRegistrationResponseJson(credencialJson);
            return parte(pendente).finishRegistration(FinishRegistrationOptions.builder().request(opcoes).response(resposta).build());
        } catch (Exception e) {
            throw new ValidationException("Não foi possível cadastrar este aparelho.", "CADASTRO_RECUSADO", null);
        }
    }

    public Opcoes iniciarEntrada(HttpServletRequest pedido) {
        var rp = parte(pedido);
        // Sem nome de usuário: credencial descoberta pelo autenticador, e a porta não diz quais existem.
        var opcoes = rp.startAssertion(StartAssertionOptions.builder()
            .userVerification(UserVerificationRequirement.REQUIRED)
            .timeout(VALIDADE_DO_PEDIDO.toMillis())
            .build());
        try {
            return guardar(Tipo.ENTRADA, opcoes.toJson(), opcoes.toCredentialsGetJson(), pedido);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vazio quando a assinatura não vale (inclui o contador que não avançou, D-107); o motivo não sai daqui. */
    public java.util.Optional<AssertionResult> concluirEntrada(HttpServletRequest pedido, String pedidoId, String credencialJson) {
        Pendente pendente;
        try {
            pendente = retirar(pedidoId, Tipo.ENTRADA, pedido);
        } catch (ValidationException e) {
            return java.util.Optional.empty();
        }
        try {
            var opcoes = AssertionRequest.fromJson(pendente.opcoesJson());
            var resposta = PublicKeyCredential.parseAssertionResponseJson(credencialJson);
            var resultado = parte(pendente).finishAssertion(FinishAssertionOptions.builder().request(opcoes).response(resposta).build());
            return resultado.isSuccess() && resultado.isSignatureCounterValid() ? java.util.Optional.of(resultado) : java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private Opcoes guardar(Tipo tipo, String opcoesJson, String navegadorJson, HttpServletRequest pedido) {
        limparVencidos();
        var id = Segredo.idDePedido();
        pendentes.put(id, new Pendente(tipo, opcoesJson, rpId(pedido), OrigemDoPedido.origem(pedido), relogio.instant().plus(VALIDADE_DO_PEDIDO)));
        return new Opcoes(id, navegadorJson);
    }

    private Pendente retirar(String pedidoId, Tipo tipo, HttpServletRequest pedido) {
        var pendente = pedidoId == null ? null : pendentes.remove(pedidoId);
        if (pendente == null || pendente.tipo() != tipo || !pendente.expira().isAfter(relogio.instant())
            || !pendente.origem().equals(OrigemDoPedido.origem(pedido))) {
            throw new ValidationException("O pedido expirou. Tente de novo.", "PEDIDO_EXPIRADO", null);
        }
        return pendente;
    }

    private void limparVencidos() {
        var agora = relogio.instant();
        pendentes.values().removeIf(p -> !p.expira().isAfter(agora));
    }

    private RelyingParty parte(HttpServletRequest pedido) {
        return montar(rpId(pedido), OrigemDoPedido.origem(pedido));
    }

    private RelyingParty parte(Pendente pendente) {
        return montar(pendente.rpId(), pendente.origem());
    }

    private RelyingParty montar(String rpId, String origem) {
        return RelyingParty.builder()
            .identity(RelyingPartyIdentity.builder().id(rpId).name("eAgora").build())
            .credentialRepository(passkeys)
            .origins(Set.of(origem))
            .validateSignatureCounter(true)
            .clock(relogio)
            .build();
    }

    String rpId(HttpServletRequest pedido) {
        var host = OrigemDoPedido.host(pedido);
        for (var permitido : hosts) {
            if (permitido.startsWith("*.") ? host.endsWith(permitido.substring(1)) : host.equals(permitido)) {
                return host;
            }
        }
        throw new ValidationException("Este endereço não pode usar passkey. Use o endereço do app no Tailscale.", "HOST_NAO_PERMITIDO", null);
    }
}
