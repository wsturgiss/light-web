package java.security.cert

import java.security.GeneralSecurityException

open class CertificateException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)
open class CertPathValidatorException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)
open class CertPathBuilderException(message: String? = null, cause: Throwable? = null) : GeneralSecurityException(message, cause)
