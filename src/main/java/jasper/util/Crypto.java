package jasper.util;

import org.apache.commons.io.output.StringBuilderWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;

public class Crypto {

    public static String writeRsaPrivatePem(PrivateKey privateKey) throws IOException {
        var pkout = new StringBuilderWriter();
        try (var writer = new PemWriter(pkout)) {
            writer.writeObject(new PemObject(
                "PRIVATE KEY",
                privateKey.getEncoded()));
        }
        return pkout.toString();
    }

    public static String writeSshRsa(RSAPublicKey publicKey, String comment) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        dos.writeInt("ssh-rsa".getBytes().length);
        dos.write("ssh-rsa".getBytes());
        dos.writeInt(publicKey.getPublicExponent().toByteArray().length);
        dos.write(publicKey.getPublicExponent().toByteArray());
        dos.writeInt(publicKey.getModulus().toByteArray().length);
        dos.write(publicKey.getModulus().toByteArray());
        return "ssh-rsa " + DatatypeConverter.printBase64Binary(os.toByteArray()) + " " + comment;
    }
}
