# libs/

Drop Android libraries here; `build.sh` dexes their classes and merges their manifests.

For real coin purchases put the **Google Play Billing Library** AAR here, for example
`billing-8.0.0.aar` from
https://maven.google.com/web/index.html#com.android.billingclient:billing
(direct link pattern: `https://dl.google.com/dl/android/maven2/com/android/billingclient/billing/<version>/billing-<version>.aar`).
Its other dependencies are not needed: `libs/compat/` has small stand-ins for the few AndroidX and
datatransport classes the library uses (its usage logging is dropped). Without the AAR the coin store
stays hidden.
