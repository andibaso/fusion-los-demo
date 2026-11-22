package sg.andi.fusionlos.workflow;

import sg.andi.fusionlos.domain.OfferChoice;

public record OfferResponse(OfferChoice choice, String actor) {}
