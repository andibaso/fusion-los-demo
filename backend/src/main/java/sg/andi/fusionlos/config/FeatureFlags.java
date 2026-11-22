package sg.andi.fusionlos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stands in for "which build of the worker is deployed". scripts/deploy-v2.sh sets LOS_FEATURE_PEP=true.
 * The flag only picks the code path for new runs; Workflow.getVersion is what keeps old runs safe.
 */
@ConfigurationProperties("los.features")
public record FeatureFlags(boolean pepCheck) {

    public String workerBuild() {
        return pepCheck ? "v2" : "v1";
    }
}
