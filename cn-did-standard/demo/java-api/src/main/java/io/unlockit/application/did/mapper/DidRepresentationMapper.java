package io.unlockit.application.did.mapper;

import io.unlockit.application.did.api.representation.DidDocumentRepresentation;
import io.unlockit.application.did.api.representation.DidRegistrationRepresentation;
import io.unlockit.application.did.api.representation.DidRepresentation;
import io.unlockit.application.did.api.representation.RegisteredDidRepresentation;
import io.unlockit.application.did.api.representation.ServiceRepresentation;
import io.unlockit.application.did.api.representation.ServiceRepresentation.EndpointRepresentation;
import io.unlockit.application.did.api.representation.ServiceRepresentation.ServiceEndpointRepresentation;
import io.unlockit.application.did.api.representation.VerificationMethodRepresentation;
import io.unlockit.application.did.api.representation.VerificationRelationshipsRepresentation;
import io.unlockit.domain.did.entity.DidDocument;
import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;

public final class DidRepresentationMapper {
    private DidRepresentationMapper() {}

    public static DidRepresentation toRepresentation(IntrinsicDid did) {
        return new DidRepresentation(did.contractId(), did.id().value(), document(did.document()));
    }

    public static RegisteredDidRepresentation toRepresentation(RegisteredDid did) {
        var registration = did.registration();
        return new RegisteredDidRepresentation(
                did.intrinsic().contractId(),
                did.intrinsic().id().value(),
                document(did.intrinsic().document()),
                new DidRegistrationRepresentation(
                        registration.registryAdmin(), registration.registeredAt(), registration.updatedAt(),
                        registration.versionId(), registration.deactivated(), registration.deactivatedAt(),
                        registration.source(), registration.integrityEvidence(), registration.finalityEvidence(),
                        registration.metadata()));
    }

    private static DidDocumentRepresentation document(DidDocument document) {
        var relationships = document.verificationRelationships();
        return new DidDocumentRepresentation(
                document.id().value(),
                document.controller().stream().map(Object::toString).toList(),
                document.verificationMethod().stream()
                        .map(method -> new VerificationMethodRepresentation(
                                method.id(), method.type(), method.controller().value(), method.publicKey()))
                        .toList(),
                new VerificationRelationshipsRepresentation(
                        relationships.authentication(), relationships.assertionMethod(),
                        relationships.capabilityInvocation(), relationships.capabilityDelegation(),
                        relationships.keyAgreement()),
                document.service().stream()
                        .map(service -> new ServiceRepresentation(
                                service.id(), service.type(), new ServiceEndpointRepresentation(
                                        service.version(), service.endpoints().stream()
                                                .map(endpoint -> new EndpointRepresentation(
                                                        endpoint.uri().toString(), endpoint.priority()))
                                                .toList())))
                        .toList());
    }
}
