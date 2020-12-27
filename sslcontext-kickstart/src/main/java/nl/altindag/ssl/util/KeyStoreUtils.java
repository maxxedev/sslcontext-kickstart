/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.altindag.ssl.util;

import javax.net.ssl.X509TrustManager;

import static java.util.Objects.isNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Hakan Altindag
 */
public final class KeyStoreUtils {

    public static final String DUMMY_PASSWORD = "dummy-password";
    private static final String KEYSTORE_TYPE = "PKCS12";

    private KeyStoreUtils() {}

    public static KeyStore loadKeyStore(String keystorePath, char[] keystorePassword) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        return loadKeyStore(keystorePath, keystorePassword, KeyStore.getDefaultType());
    }

    public static KeyStore loadKeyStore(String keystorePath, char[] keystorePassword, String keystoreType) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        try(InputStream keystoreInputStream = KeyStoreUtils.class.getClassLoader().getResourceAsStream(keystorePath)) {
            return loadKeyStore(keystoreInputStream, keystorePassword, keystoreType);
        }
    }

    public static KeyStore loadKeyStore(Path keystorePath, char[] keystorePassword) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        return loadKeyStore(keystorePath, keystorePassword, KeyStore.getDefaultType());
    }

    public static KeyStore loadKeyStore(Path keystorePath, char[] keystorePassword, String keystoreType) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        try(InputStream keystoreInputStream = Files.newInputStream(keystorePath, StandardOpenOption.READ)) {
            return loadKeyStore(keystoreInputStream, keystorePassword, keystoreType);
        }
    }

    public static KeyStore loadKeyStore(InputStream keystoreInputStream, char[] keystorePassword) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        return loadKeyStore(keystoreInputStream, keystorePassword, KeyStore.getDefaultType());
    }

    public static KeyStore loadKeyStore(InputStream keystoreInputStream, char[] keystorePassword, String keystoreType) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (isNull(keystoreInputStream)) {
            throw new IOException("KeyStore is not present for the giving input");
        }

        KeyStore keystore = KeyStore.getInstance(keystoreType);
        keystore.load(keystoreInputStream, keystorePassword);
        return keystore;
    }

    public static KeyStore createIdentityStore(Key privateKey, char[] privateKeyPassword, Certificate... certificateChain) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore keyStore = createKeyStore();
        String alias = CertificateUtils.generateAlias(certificateChain[0]);
        keyStore.setKeyEntry(alias, privateKey, privateKeyPassword, certificateChain);
        return keyStore;
    }

    public static KeyStore createKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        return createKeyStore(DUMMY_PASSWORD.toCharArray());
    }

    public static KeyStore createKeyStore(char[] keyStorePassword) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        return createKeyStore(KEYSTORE_TYPE, keyStorePassword);
    }

    public static KeyStore createKeyStore(String keyStoreType, char[] keyStorePassword) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, keyStorePassword);
        return keyStore;
    }

    @SafeVarargs
    public static <T extends X509TrustManager> KeyStore createTrustStore(T... trustManagers) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        List<X509Certificate> certificates = new ArrayList<>();
        for (T trustManager : trustManagers) {
            certificates.addAll(Arrays.asList(trustManager.getAcceptedIssuers()));
        }
        return createTrustStore(certificates);
    }

    @SafeVarargs
    public static <T extends Certificate> KeyStore createTrustStore(T... certificates) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        return createTrustStore(Arrays.asList(certificates));
    }

    public static <T extends Certificate> KeyStore createTrustStore(List<T> certificates) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore trustStore = createKeyStore();
        for (T certificate : certificates) {
            trustStore.setCertificateEntry(CertificateUtils.generateAlias(certificate), certificate);
        }
        return trustStore;
    }

    public static List<KeyStore> loadSystemKeyStores() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        List<KeyStore> keyStores = new ArrayList<>();
        String operatingSystem = System.getProperty("os.name").toLowerCase();
        if (operatingSystem.contains("windows")) {
            KeyStore windowsRootKeyStore = createKeyStore("Windows-ROOT", null);
            KeyStore windowsMyKeyStore = createKeyStore("Windows-MY", null);

            keyStores.add(windowsRootKeyStore);
            keyStores.add(windowsMyKeyStore);
        }

        if (operatingSystem.contains("mac")) {
            KeyStore macKeyStore = createKeyStore("KeychainStore", null);
            keyStores.add(macKeyStore);
        }

        return keyStores;
    }

}
