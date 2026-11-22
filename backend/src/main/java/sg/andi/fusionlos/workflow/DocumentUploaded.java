package sg.andi.fusionlos.workflow;

import sg.andi.fusionlos.domain.DocumentType;

public record DocumentUploaded(DocumentType type, String fileRef, String actor) {}
