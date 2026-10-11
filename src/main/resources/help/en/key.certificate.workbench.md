# Key and certificate formats

Detection and parsing support only formats exposed by KeyCertificateFormatService. Conversion depends on the detected material and selected destination; password is needed only for protected formats. Validation compares the provided pair/chain according to the implemented service and is not a trust-store decision. Private material remains sensitive.
