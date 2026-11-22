package sg.andi.fusionlos.config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

@Configuration
public class TemporalConfig {

    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs workflowServiceStubs(LosProperties props) throws IOException {
        WorkflowServiceStubsOptions.Builder options = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(props.address())
                // Queries need a live worker. Fail fast so the UI stays responsive when the worker is down.
                .setRpcQueryTimeout(Duration.ofSeconds(2));

        if (props.usesTls()) {
            try (InputStream cert = new FileInputStream(props.tlsCertPath());
                 InputStream key = new FileInputStream(props.tlsKeyPath())) {
                options.setSslContext(SimpleSslContextBuilder.forPKCS8(cert, key).build());
            }
        }
        return WorkflowServiceStubs.newServiceStubs(options.build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs, LosProperties props) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder().setNamespace(props.namespace()).build());
    }
}
