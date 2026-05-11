/**
 * CertificateGenerator.java
 * Generates a temporary self-signed TLS certificate using keytool (bundled
 * with every JDK). QUIC mandates TLS 1.3 — this is protocol plumbing.
 */

import java.io.*;
import java.security.KeyStore;

class CertificateGenerator {

    KeyStore generate() throws Exception {
        File ksFile = new File("chat-server.p12");

        if (!ksFile.exists()) {
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-keystore", ksFile.getAbsolutePath(),
                    "-storetype", "PKCS12",
                    "-storepass", "password",
                    "-keypass",   "password",
                    "-alias",     "chat-server",
                    "-keyalg",    "EC",
                    "-groupname", "secp256r1",
                    "-dname",     "CN=localhost",
                    "-validity",  "1"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            new BufferedReader(new InputStreamReader(p.getInputStream()))
                    .lines().forEach(l -> {});   // drain output
            int exit = p.waitFor();
            if (exit != 0) throw new RuntimeException("keytool failed (exit " + exit + ")");
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, "password".toCharArray());
        }
        return ks;
    }
}
