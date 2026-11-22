package sg.andi.fusionlos.activity;

import org.springframework.stereotype.Component;
import sg.andi.fusionlos.config.DemoClock;
import sg.andi.fusionlos.config.DurationProfiles;
import sg.andi.fusionlos.config.RuntimeSettings;
import sg.andi.fusionlos.domain.LoanApplicationRepository;

@Component
public class ConfigActivitiesImpl implements ConfigActivities {

    private final DurationProfiles durations;
    private final RuntimeSettings settings;
    private final LoanApplicationRepository applications;

    public ConfigActivitiesImpl(DurationProfiles durations, RuntimeSettings settings,
                                LoanApplicationRepository applications) {
        this.durations = durations;
        this.settings = settings;
        this.applications = applications;
    }

    @Override
    public DemoClock durations(String applicationId) {
        return durations.get(applications.get(applicationId).durationsProfile());
    }

    @Override
    public boolean parallelVerification() {
        return settings.parallelVerification();
    }
}
