package it.northleap.backend.services;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeUrlValidatorTest {

    // resolver finto: nessuna I/O di rete, InetAddress.getByAddress non fa lookup DNS, costruisce
    // solo l'oggetto a partire dai byte grezzi forniti
    private static InetAddress fakeAddress(int... octets) throws UnknownHostException {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) octets[i];
        }
        return InetAddress.getByAddress(bytes);
    }

    // IPv6 da 16 byte grezzi (stesso principio: nessuna I/O, solo costruzione dell'oggetto)
    private static InetAddress fakeAddress6(int... octets) throws UnknownHostException {
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) octets[i];
        }
        return InetAddress.getByAddress(bytes);
    }

    private static SafeUrlValidator.DnsResolver resolvesTo(InetAddress... addresses) {
        return host -> addresses;
    }

    @Test
    void allowsPublicHttpsUrlThatResolvesToAPublicIp() throws Exception {
        assertTrue(SafeUrlValidator.isSafe("https://example.com/webhook", resolvesTo(fakeAddress(93, 184, 216, 34))));
    }

    @Test
    void allowsPublicHttpUrlThatResolvesToAPublicIp() throws Exception {
        assertTrue(SafeUrlValidator.isSafe("http://example.com/webhook", resolvesTo(fakeAddress(93, 184, 216, 34))));
    }

    @Test
    void blocksNonHttpScheme() {
        assertFalse(SafeUrlValidator.isSafe("file:///etc/passwd"));
        assertFalse(SafeUrlValidator.isSafe("ftp://example.com/file"));
    }

    @Test
    void blocksMalformedUrl() {
        assertFalse(SafeUrlValidator.isSafe("not a url"));
        assertFalse(SafeUrlValidator.isSafe(""));
    }

    @Test
    void blocksLocalhostAndLoopback() {
        assertFalse(SafeUrlValidator.isSafe("http://localhost/secrets"));
        assertFalse(SafeUrlValidator.isSafe("http://0.0.0.0/secrets"));
        assertFalse(SafeUrlValidator.isSafe("http://127.0.0.1/secrets"));
    }

    @Test
    void blocksLocalAndInternalSuffixes() {
        assertFalse(SafeUrlValidator.isSafe("http://service.local/api"));
        assertFalse(SafeUrlValidator.isSafe("http://service.internal/api"));
    }

    // IP letterali nell'URL: InetAddress.getAllByName non fa una vera query DNS per una stringa
    // che e' gia' un indirizzo IP testuale - sicuro da chiamare anche nel 1-arg facade reale
    @Test
    void blocksPrivateIpv4Ranges() {
        assertFalse(SafeUrlValidator.isSafe("http://10.0.0.5/api"));
        assertFalse(SafeUrlValidator.isSafe("http://192.168.1.1/api"));
        assertFalse(SafeUrlValidator.isSafe("http://172.16.0.1/api"));
        assertFalse(SafeUrlValidator.isSafe("http://172.31.255.255/api"));
        assertTrue(SafeUrlValidator.isSafe("http://172.32.0.1/api"));
        assertTrue(SafeUrlValidator.isSafe("http://172.15.255.255/api"));
    }

    @Test
    void blocksLinkLocalAndCloudMetadataEndpoint() {
        assertFalse(SafeUrlValidator.isSafe("http://169.254.169.254/latest/meta-data"));
        assertFalse(SafeUrlValidator.isSafe("http://169.254.0.1/api"));
    }

    // il caso che il fix chiude: un hostname pubblico (passa tutti i controlli letterali) che
    // pero' risolve via DNS a un IP privato - DNS rebinding
    @Test
    void blocksHostnameThatResolvesToAPrivateIpViaDnsRebinding() throws Exception {
        assertFalse(SafeUrlValidator.isSafe("http://evil-rebind.example/webhook", resolvesTo(fakeAddress(127, 0, 0, 1))));
        assertFalse(SafeUrlValidator.isSafe("http://evil-rebind.example/webhook", resolvesTo(fakeAddress(10, 0, 0, 1))));
        assertFalse(SafeUrlValidator.isSafe("http://evil-rebind.example/webhook", resolvesTo(fakeAddress(169, 254, 169, 254))));
    }

    @Test
    void blocksHostnameWithAnyResolvedAddressInAPrivateRange() throws Exception {
        // basta che UNO degli IP risolti sia privato, anche se ce ne sono altri pubblici
        assertFalse(SafeUrlValidator.isSafe("http://multi.example/webhook",
                resolvesTo(fakeAddress(93, 184, 216, 34), fakeAddress(127, 0, 0, 1))));
    }

    @Test
    void blocksUnresolvableHostname() {
        SafeUrlValidator.DnsResolver throwing = host -> {
            throw new UnknownHostException(host);
        };
        assertFalse(SafeUrlValidator.isSafe("http://does-not-exist.invalid/webhook", throwing));
    }
}
