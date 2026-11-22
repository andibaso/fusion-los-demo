package sg.andi.fusionlos.workflow;

import io.temporal.common.SearchAttributeKey;

/** Search attributes registered on the Temporal server. Used to filter in the Temporal UI. */
public final class LosSearchAttributes {

    private LosSearchAttributes() {}

    public static final SearchAttributeKey<String> STAGE = SearchAttributeKey.forKeyword("Stage");
    public static final SearchAttributeKey<String> STATUS = SearchAttributeKey.forKeyword("Status");
    public static final SearchAttributeKey<Double> AMOUNT = SearchAttributeKey.forDouble("Amount");
    public static final SearchAttributeKey<String> ASSIGNED_TO = SearchAttributeKey.forKeyword("AssignedTo");
}
