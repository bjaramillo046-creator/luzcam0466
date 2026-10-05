package cl.luzcam;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Shared-secret authentication and AES-GCM records; no Android dependencies. */
public final class SecureChannel implements Closeable {
    private final Socket socket = new Socket();
    private DataOutputStream out;
    private byte[] key;
    private long counter;
    private static final byte[] MAGIC = new byte[]{76,67,87,49};

    public static byte[] mac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    public void connect(String host, String token) throws Exception {
        token = token.replace("-", "").replace(" ", "").trim();
        if (!token.matches("[a-fA-F0-9]{32}")) throw new IOException("Código inválido: deben ser 32 caracteres");
        byte[] secret = new byte[16];
        for (int i=0;i<16;i++) secret[i]=(byte)Integer.parseInt(token.substring(i*2,i*2+2),16);
        socket.connect(new InetSocketAddress(host, 47990), 4000);
        socket.setSoTimeout(5000);
        socket.setTcpNoDelay(true);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        byte[] magic = new byte[4]; in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("El receptor no es LuzCam v1");
        byte[] server = new byte[32]; in.readFully(server);
        byte[] client = new byte[32]; new SecureRandom().nextBytes(client);
        ByteBuffer material = ByteBuffer.allocate(71).put(ascii("session")).put(server).put(client);
        key = mac(secret, material.array());
        out.write(client); out.write(mac(key, ascii("client"))); out.flush();
        byte[] proof = new byte[32]; in.readFully(proof);
        if (!MessageDigest.isEqual(proof, mac(key, ascii("server")))) throw new IOException("Código incorrecto o receptor no válido");
        counter=0;
    }
    public void send(byte[] jpeg, int rotation) throws Exception {
        byte[] plaintext = ByteBuffer.allocate(jpeg.length+2).putShort((short)rotation).put(jpeg).array();
        if (plaintext.length + 16 > 2000000) throw new IOException("Imagen demasiado grande");
        byte[] nonce = ByteBuffer.allocate(12).putInt(0).putLong(counter++).array();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,nonce));
        c.updateAAD(MAGIC);
        byte[] encrypted = c.doFinal(plaintext);
        out.writeInt(encrypted.length); out.write(encrypted); out.flush();
    }
    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
