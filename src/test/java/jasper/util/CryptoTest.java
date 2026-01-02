package jasper.util;

import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CryptoTest {

@Test
void testKeyPairGeneration() throws NoSuchAlgorithmException {
var keyPair = Crypto.keyPair();

assertThat(keyPair).isNotNull();
assertThat(keyPair.getPrivate()).isNotNull();
assertThat(keyPair.getPublic()).isNotNull();
assertThat(keyPair.getPrivate().getAlgorithm()).isEqualTo("RSA");
assertThat(keyPair.getPublic().getAlgorithm()).isEqualTo("RSA");
}

@Test
void testKeyPairGenerates4096BitKey() throws NoSuchAlgorithmException {
var keyPair = Crypto.keyPair();
var publicKey = (RSAPublicKey) keyPair.getPublic();

// 4096 bit key
assertThat(publicKey.getModulus().bitLength()).isEqualTo(4096);
}

@Test
void testWriteRsaPrivatePem() throws Exception {
var keyPair = Crypto.keyPair();
var pem = Crypto.writeRsaPrivatePem(keyPair.getPrivate());

assertThat(pem).isNotNull();
assertThat(pem).contains("-----BEGIN PRIVATE KEY-----");
assertThat(pem).contains("-----END PRIVATE KEY-----");
assertThat(pem.length()).isGreaterThan(100);
}

@Test
void testWriteRsaPrivatePemIsValidFormat() throws Exception {
var keyPair = Crypto.keyPair();
var pem = Crypto.writeRsaPrivatePem(keyPair.getPrivate());

var lines = pem.split("\n");
assertThat(lines[0]).contains("BEGIN PRIVATE KEY");
assertThat(lines[lines.length - 1]).contains("END PRIVATE KEY");
}

@Test
void testWriteSshRsa() throws Exception {
var keyPair = Crypto.keyPair();
var publicKey = (RSAPublicKey) keyPair.getPublic();
var comment = "test@example.com";

var sshKey = Crypto.writeSshRsa(publicKey, comment);

assertThat(sshKey).isNotNull();
assertThat(sshKey).startsWith("ssh-rsa ");
assertThat(sshKey).endsWith(" " + comment);
assertThat(sshKey.length()).isGreaterThan(50);
}

@Test
void testWriteSshRsaFormat() throws Exception {
var keyPair = Crypto.keyPair();
var publicKey = (RSAPublicKey) keyPair.getPublic();

var sshKey = Crypto.writeSshRsa(publicKey, "comment");

var parts = sshKey.split(" ");
assertThat(parts).hasSize(3);
assertThat(parts[0]).isEqualTo("ssh-rsa");
assertThat(parts[2]).isEqualTo("comment");
assertThat(parts[1]).matches("[A-Za-z0-9+/=]+");
}

@Test
void testDifferentKeyPairsProduceDifferentKeys() throws Exception {
var keyPair1 = Crypto.keyPair();
var keyPair2 = Crypto.keyPair();

var pem1 = Crypto.writeRsaPrivatePem(keyPair1.getPrivate());
var pem2 = Crypto.writeRsaPrivatePem(keyPair2.getPrivate());

assertThat(pem1).isNotEqualTo(pem2);
}

@Test
void testKeyPairConsistency() throws Exception {
var keyPair = Crypto.keyPair();
var publicKey = (RSAPublicKey) keyPair.getPublic();

var ssh1 = Crypto.writeSshRsa(publicKey, "comment");
var ssh2 = Crypto.writeSshRsa(publicKey, "comment");

assertThat(ssh1).isEqualTo(ssh2);
}

@Test
void testWriteRsaPrivatePemNullKey() {
assertThatThrownBy(() -> Crypto.writeRsaPrivatePem(null))
.isInstanceOf(NullPointerException.class);
}
}
