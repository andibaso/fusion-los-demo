package sg.andi.fusionlos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Temporal connection settings. TLS fields are only needed for Temporal Cloud. */
@ConfigurationProperties("los.temporal")
public record LosProperties(
        String address,
        String namespace,
        String tlsCertPath,
        String tlsKeyPath) {

    public boolean usesTls() {
        return tlsCertPath != null && !tlsCertPath.isBlank();
    }
}
