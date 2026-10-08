"""Framework-agnostic signature verification and signing, for integrators
mounting into their own stack.
"""

from t0_provider_sdk.common.headers import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.signer import SignFn, new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature
from t0_provider_sdk.provider.errors import (
    BodyTooLargeError,
    InvalidHeaderEncodingError,
    MissingRequiredHeaderError,
    SignatureFailedError,
    SignatureVerificationError,
    TimestampOutOfRangeError,
    UnknownPublicKeyError,
)
from t0_provider_sdk.provider.interceptor import (
    SignatureErrorInterceptor,
    SignatureErrorInterceptorSync,
)
from t0_provider_sdk.provider.middleware import (
    VerifySignatureFn,
    new_verify_signature,
    signature_error_var,
)

__all__ = [
    "BodyTooLargeError",
    "InvalidHeaderEncodingError",
    "MissingRequiredHeaderError",
    "PUBLIC_KEY_HEADER",
    "SIGNATURE_HEADER",
    "SIGNATURE_TIMESTAMP_HEADER",
    "SignFn",
    "SignatureErrorInterceptor",
    "SignatureErrorInterceptorSync",
    "SignatureFailedError",
    "SignatureVerificationError",
    "TimestampOutOfRangeError",
    "UnknownPublicKeyError",
    "VerifySignatureFn",
    "legacy_keccak256",
    "new_signer_from_hex",
    "new_verify_signature",
    "signature_error_var",
    "verify_signature",
]
