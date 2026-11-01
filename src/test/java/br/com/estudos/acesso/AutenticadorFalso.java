package br.com.estudos.acesso;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Um passkey de mentira para os testes (docs/SPRINT-30-LOGIN.md §6): chave P-256, atestação "none", CBOR escrito à mão.
 * Responde às opções que o servidor manda como o navegador responderia, em JSON com base64url.
 */
final class AutenticadorFalso {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    final byte[] credencialId = new byte[16];
    private final KeyPair chaves;
    /** O contador que a próxima assinatura manda; `null` = não conta (sempre 0, como os passkeys sincronizados). */
    Long contador;
    private byte[] userHandle;

    AutenticadorFalso(Long contadorInicial) throws Exception {
        new java.security.SecureRandom().nextBytes(credencialId);
        var gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        chaves = gerador.generateKeyPair();
        contador = contadorInicial;
    }

    /** Resposta a `{pedidoId, opcoes:{publicKey}}` de cadastro. */
    String cadastrar(String opcoesJson, String origem) throws Exception {
        var pk = JSON.readTree(opcoesJson).get("opcoes").get("publicKey");
        var rpId = pk.get("rp").get("id").asString();
        userHandle = D64.decode(pk.get("user").get("id").asString());
        var cliente = clientData("webauthn.create", pk.get("challenge").asString(), origem);

        var dados = new ByteArrayOutputStream();
        dados.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
        dados.write(0x45); // UP | UV | AT
        dados.write(ByteBuffer.allocate(4).putInt((int) contadorAtual()).array());
        dados.write(new byte[16]); // AAGUID
        dados.write(ByteBuffer.allocate(2).putShort((short) credencialId.length).array());
        dados.write(credencialId);
        dados.write(coseDaChave());

        var atestacao = new LinkedHashMap<Object, Object>();
        atestacao.put("fmt", "none");
        atestacao.put("attStmt", new LinkedHashMap<>());
        atestacao.put("authData", dados.toByteArray());

        var resposta = new LinkedHashMap<String, Object>();
        resposta.put("clientDataJSON", B64.encodeToString(cliente));
        resposta.put("attestationObject", B64.encodeToString(cbor(atestacao)));
        return credencial(resposta);
    }

    /** Resposta a `{pedidoId, opcoes:{publicKey}}` de entrada. */
    String assinar(String opcoesJson, String origem) throws Exception {
        var pk = JSON.readTree(opcoesJson).get("opcoes").get("publicKey");
        var rpId = pk.get("rpId").asString();
        var cliente = clientData("webauthn.get", pk.get("challenge").asString(), origem);
        var dados = ByteBuffer.allocate(37).put(sha256(rpId.getBytes(StandardCharsets.UTF_8))).put((byte) 0x05)
            .putInt((int) contadorAtual()).array();
        var assinatura = Signature.getInstance("SHA256withECDSA");
        assinatura.initSign(chaves.getPrivate());
        assinatura.update(dados);
        assinatura.update(sha256(cliente));

        var resposta = new LinkedHashMap<String, Object>();
        resposta.put("clientDataJSON", B64.encodeToString(cliente));
        resposta.put("authenticatorData", B64.encodeToString(dados));
        resposta.put("signature", B64.encodeToString(assinatura.sign()));
        resposta.put("userHandle", B64.encodeToString(userHandle));
        return credencial(resposta);
    }

    static String pedidoId(String opcoesJson) {
        return JSON.readTree(opcoesJson).get("pedidoId").asString();
    }

    static JsonNode arvore(String json) {
        return JSON.readTree(json);
    }

    private long contadorAtual() {
        return contador == null ? 0 : contador;
    }

    private String credencial(Map<String, Object> resposta) {
        var credencial = new LinkedHashMap<String, Object>();
        credencial.put("type", "public-key");
        credencial.put("id", B64.encodeToString(credencialId));
        credencial.put("rawId", B64.encodeToString(credencialId));
        credencial.put("response", resposta);
        credencial.put("clientExtensionResults", Map.of());
        return JSON.writeValueAsString(credencial);
    }

    private static byte[] clientData(String tipo, String desafio, String origem) {
        return ("{\"type\":\"" + tipo + "\",\"challenge\":\"" + desafio + "\",\"origin\":\"" + origem + "\",\"crossOrigin\":false}")
            .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] coseDaChave() {
        var ponto = ((ECPublicKey) chaves.getPublic()).getW();
        var cose = new LinkedHashMap<Object, Object>();
        cose.put(1, 2);   // kty: EC2
        cose.put(3, -7);  // alg: ES256
        cose.put(-1, 1);  // crv: P-256
        cose.put(-2, coordenada(ponto.getAffineX()));
        cose.put(-3, coordenada(ponto.getAffineY()));
        return cbor(cose);
    }

    private static byte[] coordenada(BigInteger valor) {
        var bytes = valor.toByteArray();
        var saida = new byte[32];
        var inicio = Math.max(0, bytes.length - 32);
        System.arraycopy(bytes, inicio, saida, 32 - (bytes.length - inicio), bytes.length - inicio);
        return saida;
    }

    private static byte[] sha256(byte[] dados) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(dados);
    }

    // --- CBOR mínimo: inteiro, texto, bytes e mapa ------------------------------------------------------------------

    private static byte[] cbor(Object valor) {
        var saida = new ByteArrayOutputStream();
        escrever(saida, valor);
        return saida.toByteArray();
    }

    private static void escrever(ByteArrayOutputStream saida, Object valor) {
        if (valor instanceof Integer n) {
            if (n >= 0) {
                cabecalho(saida, 0, n);
            } else {
                cabecalho(saida, 1, -1L - n);
            }
        } else if (valor instanceof String s) {
            var b = s.getBytes(StandardCharsets.UTF_8);
            cabecalho(saida, 3, b.length);
            saida.writeBytes(b);
        } else if (valor instanceof byte[] b) {
            cabecalho(saida, 2, b.length);
            saida.writeBytes(b);
        } else if (valor instanceof Map<?, ?> m) {
            cabecalho(saida, 5, m.size());
            m.forEach((k, v) -> {
                escrever(saida, k);
                escrever(saida, v);
            });
        } else {
            throw new IllegalArgumentException("CBOR: tipo não suportado " + valor);
        }
    }

    private static void cabecalho(ByteArrayOutputStream saida, int tipo, long n) {
        int t = tipo << 5;
        if (n < 24) {
            saida.write(t | (int) n);
        } else if (n < 256) {
            saida.write(t | 24);
            saida.write((int) n);
        } else if (n < 65536) {
            saida.write(t | 25);
            saida.write((int) (n >> 8));
            saida.write((int) n & 0xff);
        } else {
            saida.write(t | 26);
            saida.writeBytes(ByteBuffer.allocate(4).putInt((int) n).array());
        }
    }
}
