package it.northleap.backend.services;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Guardia anti-SSRF, porting di isSafeUrl in workflow.engine.ts — usata da WorkflowActionExecutor
// prima di QUALSIASI richiesta verso un URL fornito dalla configurazione di un workflow.
//
// Rispetto all'originale, qui risolviamo anche il DNS dell'hostname e verifichiamo OGNI IP
// risultante: l'originale (e la prima versione di questo porting) controllava solo la stringa
// letterale nell'URL, quindi un dominio pubblico che fa DNS rebinding verso un IP privato
// bypassava il controllo. Chiusura deliberata di un gap segnalato in security review - se la
// risoluzione DNS fallisce, l'URL è considerato non sicuro (non possiamo verificarne la
// destinazione, quindi non lo permettiamo).
public final class SafeUrlValidator {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private SafeUrlValidator() {
    }

    @FunctionalInterface
    interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static boolean isSafe(String rawUrl) {
        return isSafe(rawUrl, InetAddress::getAllByName);
    }

    // seam di testabilità: stesso pattern già usato altrove nel progetto (parametro esplicito al
    // posto di una dipendenza reale) per evitare lookup DNS veri nei test - i test passano un
    // resolver finto che costruisce InetAddress da byte grezzi (InetAddress.getByAddress), senza
    // alcuna I/O di rete
    static boolean isSafe(String rawUrl, DnsResolver resolver) {
        URI u;
        try {
            u = new URI(rawUrl);
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
        String scheme = u.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return false;
        }
        if (u.getHost() == null || u.getHost().isEmpty()) {
            return false;
        }
        String host = u.getHost().toLowerCase();
        if (host.equals("localhost") || host.equals("0.0.0.0") || host.endsWith(".local") || host.endsWith(".internal")) {
            return false;
        }
        if (isBlockedIpv4Literal(host)) {
            return false;
        }

        // host IPv6 letterale: URI.getHost() lo restituisce tra parentesi quadre ([::1]), che
        // InetAddress non accetta — vanno tolte prima della risoluzione
        String resolveHost = (host.startsWith("[") && host.endsWith("]"))
                ? host.substring(1, host.length() - 1)
                : host;

        InetAddress[] resolved;
        try {
            resolved = resolver.resolve(resolveHost);
        } catch (UnknownHostException e) {
            return false;
        }
        for (InetAddress addr : resolved) {
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                    || isUniqueLocalIpv6(addr)) {
                return false;
            }
        }
        return true;
    }

    // IPv6 Unique Local Address (fc00::/7): non intercettato da isSiteLocalAddress() (che copre
    // solo il deprecato fec0::/10 per IPv6), quindi un ULA bypasserebbe la guardia senza questo
    private static boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private static boolean isBlockedIpv4Literal(String host) {
        Matcher m = IPV4.matcher(host);
        if (!m.matches()) {
            return false;
        }
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31) || (a == 169 && b == 254);
    }
}
