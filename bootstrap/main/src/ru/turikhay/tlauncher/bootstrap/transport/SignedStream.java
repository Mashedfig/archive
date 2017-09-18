package ru.turikhay.tlauncher.bootstrap.transport;

import ru.turikhay.tlauncher.bootstrap.util.Sha256Sign;
import ru.turikhay.tlauncher.bootstrap.util.U;
import shaded.org.apache.commons.io.IOUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public class SignedStream extends ChecksumStream {
    static final PublicKey PUBLIC_KEY;

    static {
        PublicKey publicKey;
        try {
            URL url = SignedStream.class.getResource("/public.der");
            U.requireNotNull(url, "could not find public.der");
            X509EncodedKeySpec e = new X509EncodedKeySpec(IOUtils.toByteArray(url));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            publicKey = keyFactory.generatePublic(e);
        } catch(Exception e) {
            throw new Error("no public key?", e);
        }
        PUBLIC_KEY = publicKey;
    }

    byte[] signedChecksum;

    public SignedStream(InputStream stream) {
        super(stream);
    }

    @Override
    public int read() throws IOException {
        readSignature();
        int r = super.read();
        if(r == -1) {
            validateSignature();
        }
        return r;
    }

    public int read(byte b[]) throws IOException {
        readSignature();
        int r = super.read(b);
        if(r == -1) {
            validateSignature();
        }
        return r;
    }

    public int read(byte b[], int off, int len) throws IOException {
        readSignature();
        int r = super.read(b, off, len);
        if(r == -1) {
            validateSignature();
        }
        return r;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    // false if already read
    boolean readSignature() throws IOException {
        if(signedChecksum != null) {
            return false;
        }
        byte[] signature = new byte[256];
        int signatureLength = IOUtils.read(in, signature);
        if(signatureLength != 256) {
            throw new StreamNotSignedException("invalid signedChecksum length; expected: 256, got: " + signatureLength);
        }
        this.signedChecksum = signature;
        return true;
    }

    void validateSignature() throws IOException {
        if(signedChecksum == null) {
            throw new StreamNotSignedException("signedChecksum not read yet");
        }
        byte[] digest = digest();
        if(digest == null) {
            throw new IOException("calc is not calculated");
        }
        byte[] perCharChecksum = Sha256Sign.toString(digest).getBytes(U.UTF8);
        if (!verify(perCharChecksum, signedChecksum)) {
            throw new InvalidStreamSignatureException();
        }
    }

    static boolean verify(byte[] data, byte[] signature) {
        Signature signatureRef;
        try {
            signatureRef = Signature.getInstance("SHA1withRSA");
            signatureRef.initVerify(PUBLIC_KEY);
            signatureRef.update(data);
            return signatureRef.verify(signature);
        } catch(Exception e) {
            throw new Error("could not perform verification", e);
        }
    }
}
