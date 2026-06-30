package org.kfh.aiops.plugin.security;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Supplies connector WebClients with either default JVM TLS verification or explicit relaxed certificate validation.
 *
 * <p>The relaxed client still uses HTTPS transport encryption, but does not validate the remote certificate chain.
 * Connector validators keep the default as verified TLS; operators must explicitly set {@code verifySsl=false} per connector.
 */
@Component
public class ConnectorTlsWebClientFactory {

    private final WebClient verifiedClient;
    private final WebClient relaxedCertificateClient;

    @Autowired
    public ConnectorTlsWebClientFactory(WebClient.Builder builder) {
        this(builder.clone().build(), relaxedCertificateClient(builder.clone()));
    }

    ConnectorTlsWebClientFactory(WebClient verifiedClient, WebClient relaxedCertificateClient) {
        this.verifiedClient = verifiedClient;
        this.relaxedCertificateClient = relaxedCertificateClient;
    }

    public WebClient client(boolean verifySsl) {
        return verifySsl ? verifiedClient : relaxedCertificateClient;
    }

    private static WebClient relaxedCertificateClient(WebClient.Builder builder) {
        try {
            var sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            var httpClient = HttpClient.create().secure(ssl -> ssl.sslContext(sslContext));
            return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        } catch (SSLException ex) {
            throw new IllegalStateException("Unable to initialize relaxed TLS connector WebClient", ex);
        }
    }
}

