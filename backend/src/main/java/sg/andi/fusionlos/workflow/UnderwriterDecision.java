package sg.andi.fusionlos.workflow;

import sg.andi.fusionlos.domain.Decision;
import sg.andi.fusionlos.domain.DocumentType;

/** Signal payload. Item is only used with REQUEST_INFO. */
public record UnderwriterDecision(Decision decision, String actor, String note, DocumentType item) {}
