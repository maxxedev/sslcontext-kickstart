package nl.altindag.sslcontext;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.security.auth.x500.X500Principal;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.Test;

import nl.altindag.sslcontext.util.KeystoreUtils;

@SuppressWarnings({ "squid:S1192", "squid:S2068"})
public class SSLContextHelperShould {

    private static final String GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE = "Identity details are empty, which are required to be present when SSL/TLS is enabled";
    private static final String GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE = "TrustStore details are empty, which are required to be present when SSL/TLS is enabled";

    private static final String IDENTITY_FILE_NAME = "identity.jks";
    private static final String TRUSTSTORE_FILE_NAME = "truststore.jks";

    private static final String IDENTITY_PASSWORD = "secret";
    private static final String TRUSTSTORE_PASSWORD = "secret";
    private static final String KEYSTORE_LOCATION = "keystores-for-unit-tests/";
    private static final String TEMPORALLY_KEYSTORE_LOCATION = System.getProperty("user.home");

    @Test
    public void createSSLContextForOneWayAuthenticationWithOnlyJdkTrustedCertificates() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withOnlyDefaultJdkTrustStore(true)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustStore()).isNull();
        assertThat(sslContextHelper.getTrustStorePassword()).isNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).hasSizeGreaterThan(10);
    }

    @Test
    public void createSSLContextForOneWayAuthenticationWithJdkTrustedCertificatesAndCustomTrustStore() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                                                            .withOnlyDefaultJdkTrustStore(true)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getTrustStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslContextHelper.getTrustedX509Certificate()).hasSizeGreaterThan(10);
        assertThat(Arrays.stream(sslContextHelper.getTrustedX509Certificate())
                         .map(X509Certificate::getSubjectX500Principal)
                         .map(X500Principal::toString)).contains("CN=*.google.com, O=Google LLC, L=Mountain View, ST=California, C=US");
    }

    @Test
    public void createSSLContextForTwoWayAuthenticationWithOnlyJdkTrustedCertificates() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore identity = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withIdentity(identity, IDENTITY_PASSWORD)
                                                            .withOnlyDefaultJdkTrustStore(true)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getKeyManagerFactory()).isNotNull();
        assertThat(sslContextHelper.getKeyManagerFactory().getKeyManagers()).isNotEmpty();
        assertThat(sslContextHelper.getIdentity()).isNotNull();
        assertThat(sslContextHelper.getIdentityPassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNull();
        assertThat(sslContextHelper.getTrustStorePassword()).isNull();
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();
    }

    @Test
    public void createSSLContextForTwoWayAutentication() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD)
                                                            .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getKeyManagerFactory()).isNotNull();
        assertThat(sslContextHelper.getKeyManagerFactory().getKeyManagers()).isNotEmpty();
        assertThat(sslContextHelper.getIdentity()).isNotNull();
        assertThat(sslContextHelper.getIdentityPassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getTrustStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();
    }

    @Test
    public void createSSLContextForTwoWayAutenticationWithPath() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withIdentity(identityPath, IDENTITY_PASSWORD)
                                                            .withTrustStore(trustStorePath, TRUSTSTORE_PASSWORD)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getKeyManagerFactory()).isNotNull();
        assertThat(sslContextHelper.getKeyManagerFactory().getKeyManagers()).isNotEmpty();
        assertThat(sslContextHelper.getIdentity()).isNotNull();
        assertThat(sslContextHelper.getIdentityPassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getTrustStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();

        Files.delete(identityPath);
        Files.delete(trustStorePath);
    }

    @Test
    public void createSSLContextForTwoWayAutenticationWithKeyStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        KeyStore identity = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);
        KeyStore trustStore = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);

        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withIdentity(identity, IDENTITY_PASSWORD)
                                                            .withTrustStore(trustStore, TRUSTSTORE_PASSWORD)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getKeyManagerFactory()).isNotNull();
        assertThat(sslContextHelper.getKeyManagerFactory().getKeyManagers()).isNotEmpty();
        assertThat(sslContextHelper.getIdentity()).isNotNull();
        assertThat(sslContextHelper.getIdentityPassword()).isEqualTo(IDENTITY_PASSWORD);

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getTrustStorePassword()).isEqualTo(TRUSTSTORE_PASSWORD);
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();
    }

    @Test
    public void createSSLContextForOneWayAuthentication() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();
    }

    @Test
    public void createSSLContextForOneWayAuthenticationWithKeyStore() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore trustStore = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);

        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(trustStore, TRUSTSTORE_PASSWORD)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();
    }

    @Test
    public void createSSLContextForOneWayAuthenticationWithPath() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(trustStorePath, TRUSTSTORE_PASSWORD)
                                                            .build();

        assertThat(sslContextHelper.isSecurityEnabled()).isTrue();
        assertThat(sslContextHelper.isOneWayAuthenticationEnabled()).isTrue();
        assertThat(sslContextHelper.isTwoWayAuthenticationEnabled()).isFalse();
        assertThat(sslContextHelper.getSslContext()).isNotNull();

        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getTrustedX509Certificate()).isNotEmpty();
        assertThat(sslContextHelper.getTrustStore()).isNotNull();
        assertThat(sslContextHelper.getX509TrustManager()).isNotNull();
        assertThat(sslContextHelper.getHostnameVerifier()).isNotNull();

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWhileProvidingWrongPassword() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, "password"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("BOOM");
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWithPathWhileProvidingWrongPassword() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(trustStorePath, "password"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("BOOM");

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForTwoWayAuthenticationWhileProvidingWrongPassword() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, "password"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("BOOM");
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForTwoWayAuthenticationWithPathWhileProvidingWrongPassword() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);

        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(identityPath, "password"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("BOOM");

        Files.delete(identityPath);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWithNullAsTrustStorePath() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore((Path) null, "secret"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWithEmptyTrustStorePassword() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(trustStorePath, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWithEmptyTrustStoreType() throws IOException {
        Path trustStorePath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, TRUSTSTORE_FILE_NAME);

        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(trustStorePath, TRUSTSTORE_PASSWORD, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(trustStorePath);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWithNullAsTrustStore() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore((KeyStore) null, TRUSTSTORE_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhenCreateSSLContextForOneWayAuthenticationWithEmptyTrustStorePasswordWhileUsingKeyStoreObject() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore trustStore = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD);

        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(trustStore, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void createSSLContextHelperWithHostnameVerifier() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                                                            .withHostnameVerifierEnabled(true)
                                                            .build();

        assertThat(sslContextHelper.getHostnameVerifier()).isInstanceOf(DefaultHostnameVerifier.class);
    }

    @Test
    public void createSSLContextHelperWithoutHostnameVerifier() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                                                            .withHostnameVerifierEnabled(false)
                                                            .build();

        assertThat(sslContextHelper.getHostnameVerifier()).isInstanceOf(NoopHostnameVerifier.class);
    }

    @Test
    public void createSSLContextWithTlsProtocolVersionOneDotOne() {
        SSLContextHelper sslContextHelper = SSLContextHelper.builder()
                                                            .withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, TRUSTSTORE_PASSWORD)
                                                            .withProtocol("TLSv1.1")
                                                            .build();

        assertThat(sslContextHelper.getSslContext()).isNotNull();
        assertThat(sslContextHelper.getSslContext().getProtocol()).isEqualTo("TLSv1.1");
    }


    @Test
    public void throwExceptionWhenKeyStoreFileIsNotFound() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(KEYSTORE_LOCATION + "not-existing-truststore.jks", TRUSTSTORE_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Could not find the keystore file");
    }

    @Test
    public void throwExceptionOneWayAuthenticationIsEnabledWhileTrustStorePathIsNotProvided() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(EMPTY, TRUSTSTORE_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionOneWayAuthenticationIsEnabledWhileTrustStorePasswordIsNotProvided() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withTrustStore(KEYSTORE_LOCATION + TRUSTSTORE_FILE_NAME, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStorePathIsNotProvided() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(EMPTY, IDENTITY_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStorePasswordIsNotProvided() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStoreTypeIsNotProvided() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStorePathIsNull() {
        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity((Path) null, IDENTITY_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStorePasswordIsNotProvidedWhileUsingPath() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);

        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(identityPath, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(identityPath);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStoreIsNull() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity((KeyStore) null, IDENTITY_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStorePasswordIsEmptyWhileUsingKeyStoreAsOBject() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore identity = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);

        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(identity, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionTwoWayAuthenticationEnabledWhileKeyStoreTypeIsNotProvidedWhileUsingPath() throws IOException {
        Path identityPath = copyKeystoreToHomeDirectory(KEYSTORE_LOCATION, IDENTITY_FILE_NAME);

        assertThatThrownBy(() -> SSLContextHelper.builder().withIdentity(identityPath, IDENTITY_PASSWORD, EMPTY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_IDENTITY_VALIDATION_EXCEPTION_MESSAGE);

        Files.delete(identityPath);
    }

    @Test
    public void throwExceptionWhileBuildingSSLContextHelperWithoutProvidingTrustStoreDetails() {
        assertThatThrownBy(() -> SSLContextHelper.builder()
                                                 .withOnlyDefaultJdkTrustStore(false)
                                                 .build())
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    @Test
    public void throwExceptionWhileBuildingSSLContextHelperForTwoWayAuthenticationWithoutProvidingTrustStoreDetails() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        KeyStore identity = KeystoreUtils.loadKeyStore(KEYSTORE_LOCATION + IDENTITY_FILE_NAME, IDENTITY_PASSWORD);

        assertThatThrownBy(() -> SSLContextHelper.builder()
                                                 .withIdentity(identity, IDENTITY_PASSWORD)
                                                 .withOnlyDefaultJdkTrustStore(false)
                                                 .build())
                .isInstanceOf(RuntimeException.class)
                .hasMessage(GENERIC_TRUSTSTORE_VALIDATION_EXCEPTION_MESSAGE);
    }

    private Path copyKeystoreToHomeDirectory(String path, String fileName) throws IOException {
        try(InputStream keystoreInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path + fileName)) {
            Path destination = Paths.get(TEMPORALLY_KEYSTORE_LOCATION, fileName);
            Files.copy(keystoreInputStream, destination, REPLACE_EXISTING);
            return destination;
        }
    }

}