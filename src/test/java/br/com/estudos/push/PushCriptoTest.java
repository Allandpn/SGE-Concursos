package br.com.estudos.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * A cifra do Web Push (ADR-048): o que o SGE cifra tem de abrir no navegador. O decifrador abaixo é uma segunda implementação,
 * escrita do lado de quem recebe (RFC 8291 §3.4), para os dois lados não errarem juntos.
 */
class PushCriptoTest {

    /** O navegador: decifra o corpo {@code aes128gcm} com a própria chave privada e o segredo de autenticação. */
    static byte[] decifrarComoNavegador(byte[] corpo, java.security.PrivateKey uaPrivada, byte[] uaPublicaBruta, byte[] auth) throws Exception {
        var buffer = ByteBuffer.wrap(corpo);
        var sal = new byte[16];
        buffer.get(sal);
        int tamanhoDoRegistro = buffer.getInt();
        assertEquals(4096, tamanhoDoRegistro);
        var asPublicaBruta = new byte[buffer.get() & 0xff];
        buffer.get(asPublicaBruta);
        var cifrado = new byte[buffer.remaining()];
        buffer.get(cifrado);

        var acordo = KeyAgreement.getInstance("ECDH");
        acordo.init(uaPrivada);
        acordo.doPhase(PushCripto.publicaDeBruta(asPublicaBruta), true);
        var segredo = acordo.generateSecret();

        var prkChave = hmac(auth, segredo);
        var ikm = hmac(prkChave, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublicaBruta, asPublicaBruta), new byte[] {1});
        var prk = hmac(sal, ikm);
        var cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 16);
        var nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 12);
        var cifra = Cipher.getInstance("AES/GCM/NoPadding");
        cifra.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        var aberto = cifra.doFinal(cifrado);
        int fim = aberto.length - 1;
        while (fim >= 0 && aberto[fim] == 0) fim--;
        assertEquals(2, aberto[fim], "o último registro termina em 0x02");
        return Arrays.copyOf(aberto, fim);
    }

    private static byte[] hmac(byte[] chave, byte[]... partes) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        for (var parte : partes) mac.update(parte);
        return mac.doFinal();
    }

    private static byte[] concat(byte[]... partes) {
        var saida = ByteBuffer.allocate(Arrays.stream(partes).mapToInt(p -> p.length).sum());
        for (var parte : partes) saida.put(parte);
        return saida.array();
    }

    /** Um aparelho de mentira: o par de chaves e o segredo que o navegador entregaria na inscrição. */
    record Aparelho(java.security.PrivateKey privada, byte[] publicaBruta, byte[] auth) {
        static Aparelho novo() throws Exception {
            var gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(new ECGenParameterSpec("secp256r1"));
            var par = gerador.generateKeyPair();
            var auth = new byte[16];
            new java.security.SecureRandom().nextBytes(auth);
            return new Aparelho(par.getPrivate(), PushCripto.publicaBruta((ECPublicKey) par.getPublic()), auth);
        }

        String p256dh() {
            return PushCripto.b64(publicaBruta);
        }

        String autenticacao() {
            return PushCripto.b64(auth);
        }
    }

    @Test
    void cifrar_abreNoNavegador_comOTextoOriginal() throws Exception {
        var aparelho = Aparelho.novo();
        var texto = "{\"titulo\":\"Material pronto\",\"corpo\":\"MPPR: 198 assuntos com material.\",\"url\":\"/#/concursos\"}".getBytes(StandardCharsets.UTF_8);
        var corpo = PushCripto.cifrar(texto, aparelho.p256dh(), aparelho.autenticacao());
        assertArrayEquals(texto, decifrarComoNavegador(corpo, aparelho.privada(), aparelho.publicaBruta(), aparelho.auth()));
    }

    @Test
    void cifrar_cadaEnvioTemSalEChaveEfemeraNovos() throws Exception {
        var aparelho = Aparelho.novo();
        var a = PushCripto.cifrar("oi".getBytes(StandardCharsets.UTF_8), aparelho.p256dh(), aparelho.autenticacao());
        var b = PushCripto.cifrar("oi".getBytes(StandardCharsets.UTF_8), aparelho.p256dh(), aparelho.autenticacao());
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    void cifrar_recusaCargaQueNaoCabeNumRegistro() throws Exception {
        var aparelho = Aparelho.novo();
        var grande = new byte[PushCripto.MAXIMO_DA_CARGA + 1];
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> PushCripto.cifrar(grande, aparelho.p256dh(), aparelho.autenticacao()));
    }

    @Test
    void jwtVapid_verificaComAChavePublica_eLevaAudienciaEValidade() throws Exception {
        var par = PushCripto.gerarVapid();
        assertEquals(65, PushCripto.deB64(par.publica()).length);
        var jwt = PushCripto.jwtVapid(par.privada(), "https://fcm.googleapis.com", "mailto:sge@localhost", 2_000_000_000L);
        var partes = jwt.split("\\.");
        assertEquals(3, partes.length);
        var corpo = new String(PushCripto.deB64(partes[1]), StandardCharsets.UTF_8);
        assertTrue(corpo.contains("\"aud\":\"https://fcm.googleapis.com\"") && corpo.contains("\"exp\":2000000000"), corpo);

        var bruta = PushCripto.deB64(partes[2]);
        assertEquals(64, bruta.length);
        var verificador = Signature.getInstance("SHA256withECDSA");
        verificador.initVerify(PushCripto.publicaDeBruta(PushCripto.deB64(par.publica())));
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verificador.verify(rawParaDer(bruta)), "a assinatura R||S tem de verificar com a chave pública do VAPID");
    }

    private static byte[] rawParaDer(byte[] raw) {
        var r = new BigInteger(1, Arrays.copyOfRange(raw, 0, 32)).toByteArray();
        var s = new BigInteger(1, Arrays.copyOfRange(raw, 32, 64)).toByteArray();
        var saida = ByteBuffer.allocate(6 + r.length + s.length);
        saida.put((byte) 0x30).put((byte) (4 + r.length + s.length)).put((byte) 2).put((byte) r.length).put(r).put((byte) 2).put((byte) s.length).put(s);
        return saida.array();
    }

    @Test
    void soServicosDePushConhecidos() {
        assertTrue(PushService.enderecoPermitido("https://fcm.googleapis.com/fcm/send/abc"));
        assertTrue(PushService.enderecoPermitido("https://updates.push.services.mozilla.com/wpush/v2/abc"));
        assertTrue(PushService.enderecoPermitido("https://web.push.apple.com/abc"));
        assertFalse(PushService.enderecoPermitido("http://fcm.googleapis.com/fcm/send/abc"), "só https");
        assertFalse(PushService.enderecoPermitido("https://fcm.googleapis.com:8443/fcm/send/abc"), "só a porta 443");
        assertFalse(PushService.enderecoPermitido("https://localhost/abc"));
        assertFalse(PushService.enderecoPermitido("https://fcm.googleapis.com.exemplo.com/abc"), "o domínio tem de terminar no serviço");
        assertFalse(PushService.enderecoPermitido("https://usuario@fcm.googleapis.com/abc"));
        assertFalse(PushService.enderecoPermitido("não é uma url"));
    }
}
