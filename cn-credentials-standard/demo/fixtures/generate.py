#!/usr/bin/env python3
"""Offline, deterministic, TEST-ONLY VC 2.0 vectors. Requires installed cryptography."""
import argparse
import base64
import hashlib
import json
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

ROOT = Path(__file__).resolve().parent
DAML = ROOT.parent / "test/daml/Canton/Network/Credentials/SignedFixtures.daml"


def compact(value):
    # RFC 8785 for this deliberately restricted vector vocabulary: string keys and
    # values, arrays, objects, booleans, and small exact integers only. No floats.
    if isinstance(value, dict):
        return "{" + ",".join(compact(k) + ":" + compact(value[k]) for k in sorted(value, key=lambda k: k.encode("utf-16-be"))) + "}"
    if isinstance(value, list):
        return "[" + ",".join(map(compact, value)) + "]"
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if type(value) is bool:
        return "true" if value else "false"
    if type(value) is int and abs(value) <= 9007199254740991:
        return str(value)
    raise ValueError("unsupported JCS value")


def b64(data):
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def unb64(text):
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def base58(data):
    alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    n = int.from_bytes(data, "big")
    result = ""
    while n:
        n, remainder = divmod(n, 58)
        result = alphabet[remainder] + result
    return "1" * (len(data) - len(data.lstrip(b"\0"))) + result


def multibase(data):
    return "z" + base58(data)


def digest(data):
    return hashlib.sha256(data).digest()


def generated():
    # Published derivation label, NOT a production secret. Never reuse this key.
    key = Ed25519PrivateKey.from_private_bytes(digest(b"cn-credentials-standard/test-only/eddsa-vc2-v1"))
    public = key.public_key().public_bytes_raw()
    did = "did:key:" + multibase(b"\xed\x01" + public)
    vm = did + "#" + did.removeprefix("did:key:")
    schema = {"$schema": "https://json-schema.org/draft/2020-12/schema",
               "$id": "https://example.org/demo/achievement-schema.json", "type": "object",
               "required": ["achievement"], "properties": {"achievement": {"type": "object"}}}
    schema_bytes = (compact(schema) + "\n").encode()
    sri = "sha256-" + base64.b64encode(digest(schema_bytes)).decode()
    schema_ref = [{"id": "https://example.org/demo/achievement-schema.json", "type": "JsonSchema", "digestSRI": sri}]
    common = {"@context": ["https://www.w3.org/ns/credentials/v2"],
              "issuer": did, "validFrom": "2026-09-18T00:00:00Z",
              "validUntil": "2030-09-18T00:00:00Z", "credentialSchema": schema_ref}
    jwt_doc = {**common, "id": "urn:demo:scenario:external-did",
               "type": ["VerifiableCredential", "DiplomaCredential"],
               "credentialSubject": [{"id": "did:key:example-student", "achievement": {
                   "name": "Diploma", "courses": ["Mathematics", "Physics"]}}]}
    embedded_doc = {**common, "id": "urn:demo:scenario:external-holderless",
                    "type": ["VerifiableCredential", "AchievementCredential"],
                    "credentialSubject": [{"achievement": {"type": "CourseCertificate", "score": 85}}]}
    header = {"alg": "EdDSA", "kid": vm, "typ": "vc+jwt"}
    signing_input = b64(compact(header).encode()) + "." + b64(compact(jwt_doc).encode())
    jwt = signing_input + "." + b64(key.sign(signing_input.encode("ascii")))
    options = {"type": "DataIntegrityProof", "cryptosuite": "eddsa-jcs-2022",
               "created": "2026-09-18T00:00:00Z", "verificationMethod": vm,
               "proofPurpose": "assertionMethod"}
    proof_hash = digest(compact({"@context": embedded_doc["@context"], **options}).encode())
    doc_hash = digest(compact(embedded_doc).encode())
    signature = key.sign(proof_hash + doc_hash)
    proof = {**options, "proofValue": multibase(signature)}
    secured = {**embedded_doc, "proof": proof}
    tampered = {**embedded_doc, "credentialSubject": [{"achievement": {
        "type": "CourseCertificate", "score": 86}}], "proof": proof}

    # Independent verification from public key, both positive and negative vectors.
    verifier = Ed25519PublicKey.from_public_bytes(public)
    verifier.verify(unb64(jwt.split(".")[2]), signing_input.encode("ascii"))
    verifier.verify(signature, proof_hash + doc_hash)
    try:
        verifier.verify(signature, proof_hash + digest(compact({k: v for k, v in tampered.items() if k != "proof"}).encode()))
    except Exception as exc:
        from cryptography.exceptions import InvalidSignature
        if not isinstance(exc, InvalidSignature):
            raise
    else:
        raise AssertionError("tampered fixture unexpectedly verified")
    assert base64.b64encode(digest(schema_bytes)).decode() == sri.removeprefix("sha256-")
    assert json.loads(unb64(jwt.split(".")[1])) == jwt_doc

    files = {"achievement-schema.json": schema_bytes,
             "external-did.jwt": (jwt + "\n").encode(),
             "external-holderless.json": (compact(secured) + "\n").encode(),
             "external-holderless-tampered.json": (compact(tampered) + "\n").encode()}
    daml = '''-- Generated by fixtures/generate.py; TEST-ONLY key, never a production trust anchor.
module Canton.Network.Credentials.SignedFixtures where

import Canton.Network.Credentials.V1
import DA.NonEmpty (NonEmpty(..))

issuerDid : Text
issuerDid = %s

schemaSRI : Text
schemaSRI = %s

schema : [CredentialSchema]
schema = [CredentialSchema "https://example.org/demo/achievement-schema.json" "JsonSchema" (Some schemaSRI)]

externalDidSecuring : Optional CredentialSecuring
externalDidSecuring = Some (EnvelopedProof "application/vc+jwt" %s)

holderlessSecuring : Optional CredentialSecuring
holderlessSecuring = Some (EmbeddedProof %s
  (NonEmpty (DataIntegrityProofSummary None "DataIntegrityProof" (Some "eddsa-jcs-2022")
    (Some "2026-09-18T00:00:00Z") None (Some %s) "assertionMethod" []) []))

-- Same typed projection as holderlessSecuring; only its signed document claim differs.
tamperedSecuring : Optional CredentialSecuring
tamperedSecuring = Some (EmbeddedProof %s
  (NonEmpty (DataIntegrityProofSummary None "DataIntegrityProof" (Some "eddsa-jcs-2022")
    (Some "2026-09-18T00:00:00Z") None (Some %s) "assertionMethod" []) []))
''' % tuple(json.dumps(x, ensure_ascii=False) for x in (did, sri, jwt, compact(secured), vm, compact(tampered), vm))
    files[DAML] = daml.encode()
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify byte-identical committed vectors")
    args = parser.parse_args()
    for path, content in generated().items():
        target = path if isinstance(path, Path) else ROOT / path
        if args.check:
            if not target.exists() or target.read_bytes() != content:
                raise SystemExit("vector mismatch: " + str(target))
        else:
            target.write_bytes(content)
    print("test vectors verified" if args.check else "test vectors generated")


if __name__ == "__main__":
    main()
