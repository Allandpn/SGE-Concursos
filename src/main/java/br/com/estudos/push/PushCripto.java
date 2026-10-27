package br.com.estudos.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A cifra e a assinatura do Web Push (ADR-048): corpo {@code aes128gcm} (RFC 8291) e cabeçalho VAPID (RFC 8292), só com a JCA.
 * O serviço de push do navegador só vê bytes cifrados; a chave VAPID só autoriza enviar a quem se inscreveu.
 */
final class PushCripto {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEB64 = Base64.getUrlDecoder();
    private static final int TAMANHO_DO_REGISTRO = 4096;
    /** Cabe num único registro: 4096 menos cabeçalho (86), delimitador (1) e etiqueta do GCM (16). */
    static final int MAXIMO_DA_CARGA = 3900;

    private PushCripto() {}

    record ParDeChaves(String publica, String privada) {}

    static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    static byte[] deB64(String texto) {
        return DEB64.decode(texto);
    }

    private static ECParameterSpec curva() throws GeneralSecurityException {
        var parametros = AlgorithmParameters.getInstance("EC");
        parametros.init(new ECGenParameterSpec("secp256r1"));
        return parametros.getParameterSpec(ECParameterSpec.class);
    }

    /** O ponto público como o navegador o entrega: 0x04 || X || Y, 65 bytes. */
    static byte[] publicaBruta(ECPublicKey chave) {
        var ponto = chave.getW();
        var saida = new byte[65];
        saida[0] = 4;
        System.arraycopy(inteiro32(ponto.getAffineX()), 0, saida, 1, 32);
        System.arraycopy(inteiro32(ponto.getAffineY()), 0, saida, 33, 32);
        return saida;
    }

    private static byte[] inteiro32(BigInteger valor) {
        var bytes = valor.toByteArray();
        var saida = new byte[32];
        int copiar = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copiar, saida, 32 - copiar, copiar);
        return saida;
    }

    static PublicKey publicaDeBruta(byte[] bruta) throws GeneralSecurityException {
        if (bruta.length != 65 || bruta[0] != 4) throw new GeneralSecurityException("Chave pública P-256 inválida.");
        var x = new BigInteger(1, Arrays.copyOfRange(bruta, 1, 33));
        var y = new BigInteger(1, Arrays.copyOfRange(bruta, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curva()));
    }

    static ParDeChaves gerarVapid() {
        try {
            var gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(new ECGenParameterSpec("secp256r1"));
            var par = gerador.generateKeyPair();
            return new ParDeChaves(b64(publicaBruta((ECPublicKey) par.getPublic())),
                b64(inteiro32(((ECPrivateKey) par.getPrivate()).getS())));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PrivateKey privadaDeBruta(String privada) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, deB64(privada)), curva()));
    }

    /** O JWT do VAPID: ES256, {@code aud} é a origem do serviço de push, validade curta. */
    static String jwtVapid(String privada, String audiencia, String assunto, long expiraEmEpoch) {
        try {
            var cabecalho = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            var corpo = b64(("{\"aud\":\"" + audiencia + "\",\"exp\":" + expiraEmEpoch + ",\"sub\":\"" + assunto + "\"}").getBytes(StandardCharsets.UTF_8));
            var assinador = Signature.getInstance("SHA256withECDSA");
            assinador.initSign(privadaDeBruta(privada));
            assinador.update((cabecalho + "." + corpo).getBytes(StandardCharsets.US_ASCII));
            return cabecalho + "." + corpo + "." + b64(derParaBruta(assinador.sign()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A JCA assina em DER (SEQUENCE de dois INTEGER); o JWT quer R || S, 32 bytes cada. */
    static byte[] derParaBruta(byte[] der) {
        int i = 2;
        if ((der[1] & 0x80) != 0) i += der[1] & 0x7f;
        var r = Arrays.copyOfRange(der, i + 2, i + 2 + der[i + 1]);
        int j = i + 2 + der[i + 1];
        var s = Arrays.copyOfRange(der, j + 2, j + 2 + der[j + 1]);
        var saida = new byte[64];
        System.arraycopy(inteiro32(new BigInteger(1, r)), 0, saida, 0, 32);
        System.arraycopy(inteiro32(new BigInteger(1, s)), 0, saida, 32, 32);
        return saida;
    }

    private static byte[] hmac(byte[] chave, byte[]... partes) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        for (var parte : partes) mac.update(parte);
        return mac.doFinal();
    }

    private static byte[] concatenar(byte[]... partes) {
        var saida = ByteBuffer.allocate(Arrays.stream(partes).mapToInt(p -> p.length).sum());
        for (var parte : partes) saida.put(parte);
        return saida.array();
    }

    /** Cifra {@code dados} para o aparelho (chave pública P-256 e segredo de autenticação que o navegador entregou). */
    static byte[] cifrar(byte[] dados, String p256dh, String autenticacao) {
        if (dados.length > MAXIMO_DA_CARGA) throw new IllegalArgumentException("Carga grande demais para um registro.");
        try {
            var uaPublicaBruta = deB64(p256dh);
            var gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair efemera = gerador.generateKeyPair();
            var asPublicaBruta = publicaBruta((ECPublicKey) efemera.getPublic());

            var acordo = KeyAgreement.getInstance("ECDH");
            acordo.init(efemera.getPrivate());
            acordo.doPhase(publicaDeBruta(uaPublicaBruta), true);
            var segredo = acordo.generateSecret();

            var prkChave = hmac(deB64(autenticacao), segredo);
            var infoChave = concatenar("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublicaBruta, asPublicaBruta);
            var ikm = hmac(prkChave, infoChave, new byte[] {1});

            var sal = new byte[16];
            ALEATORIO.nextBytes(sal);
            var prk = hmac(sal, ikm);
            var cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 16);
            var nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1}), 12);

            var cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            var cifrado = cifra.doFinal(concatenar(dados, new byte[] {2}));   // 0x02: último (e único) registro

            return concatenar(sal, ByteBuffer.allocate(4).putInt(TAMANHO_DO_REGISTRO).array(), new byte[] {(byte) asPublicaBruta.length},
                asPublicaBruta, cifrado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
