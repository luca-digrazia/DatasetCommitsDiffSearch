/**
 * The MIT License
 * Copyright (c) 2012 Graylog, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graylog2.plugin.inputs.transports.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeyUtil {
    private static final Logger LOG = LoggerFactory.getLogger(KeyUtil.class);
    private static final Joiner JOINER = Joiner.on(",").skipNulls();
    private static final Pattern KEY_PATTERN = Pattern.compile("-{5}BEGIN (?:(RSA|DSA)? )?(ENCRYPTED )?PRIVATE KEY-{5}\\r?\\n([A-Z0-9a-z+/\\r\\n]+={0,2})\\r?\\n-{5}END (?:(?:RSA|DSA)? )?(?:ENCRYPTED )?PRIVATE KEY-{5}\\r?\\n$", Pattern.MULTILINE);

    public static TrustManager[] initTrustStore(File tlsClientAuthCertFile)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        final KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        loadCertificates(trustStore, tlsClientAuthCertFile, CertificateFactory.getInstance("X.509"));

        LOG.info("TrustStore: {}, aliases: {}", trustStore, join(trustStore.aliases()));

        final TrustManagerFactory instance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        instance.init(trustStore);

        return instance.getTrustManagers();
    }

    @VisibleForTesting
    protected static void loadCertificates(KeyStore trustStore, File certFile, CertificateFactory cf)
            throws CertificateException, KeyStoreException, IOException {
        if (certFile.isFile()) {
            final Collection<? extends Certificate> certificates = cf.generateCertificates(new FileInputStream(certFile));
            int i = 0;
            for (Certificate cert : certificates) {
                final String alias = certFile.getAbsolutePath() + "_" + i;
                trustStore.setCertificateEntry(alias, cert);
                i++;
                LOG.debug("Added certificate with alias {} to trust store: {}", alias, cert);
            }
        } else if (certFile.isDirectory()) {
            for (Path f : Files.newDirectoryStream(certFile.toPath())) {
                loadCertificates(trustStore, f.toFile(), cf);
            }
        }
    }

    public static KeyManager[] initKeyStore(File tlsKeyFile, File tlsCertFile, String tlsKeyPassword)
            throws IOException, GeneralSecurityException {
        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        final Collection<? extends Certificate> certChain = cf.generateCertificates(new FileInputStream(tlsCertFile));
        final PrivateKey privateKey = loadPrivateKey(tlsKeyFile, tlsKeyPassword);
        final char[] password = Strings.nullToEmpty(tlsKeyPassword).toCharArray();
        ks.setKeyEntry("key", privateKey, password, certChain.toArray(new Certificate[certChain.size()]));

        LOG.info("KeyStore: {}, aliases: {}", ks, join(ks.aliases()));

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        return kmf.getKeyManagers();
    }

    private static String join(Enumeration<String> aliases) {
        return JOINER.join(Iterators.forEnumeration(aliases));
    }

    @VisibleForTesting
    protected static PrivateKey loadPrivateKey(File file, String password) throws IOException, GeneralSecurityException {
        try (final InputStream is = new FileInputStream(file)) {
            final byte[] keyBytes = ByteStreams.toByteArray(is);
            final String keyString = new String(keyBytes, StandardCharsets.US_ASCII);
            final Matcher m = KEY_PATTERN.matcher(keyString);
            byte[] encoded = keyBytes;

            if (m.matches()) {
                if (!Strings.isNullOrEmpty(m.group(1))) {
                    throw new IllegalArgumentException("Unsupported key type PKCS#1, please convert to PKCS#8");
                }

                encoded = BaseEncoding.base64().decode(m.group(3).replaceAll("[\\r\\n]", ""));
            }

            final EncodedKeySpec keySpec = createKeySpec(encoded, password);
            if (keySpec == null) {
                throw new IllegalArgumentException("Unsupported key type");
            }

            final KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(keySpec);
        }
    }

    private static PKCS8EncodedKeySpec createKeySpec(byte[] keyBytes, String password)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException {
        if (Strings.isNullOrEmpty(password)) {
            return new PKCS8EncodedKeySpec(keyBytes);
        }

        final EncryptedPrivateKeyInfo pkInfo = new EncryptedPrivateKeyInfo(keyBytes);
        final SecretKeyFactory kf = SecretKeyFactory.getInstance(pkInfo.getAlgName());
        final PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray());
        final SecretKey secretKey = kf.generateSecret(keySpec);

        final Cipher cipher = Cipher.getInstance(pkInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, pkInfo.getAlgParameters());

        return pkInfo.getKeySpec(cipher);
    }
}
