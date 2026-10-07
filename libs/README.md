# libs/

Drop Android libraries here; `build.sh` dexes their classes and merges their manifests.

For real coin purchases put the **Google Play Billing Library** AAR here, for example
`billing-8.0.0.aar` from
https://maven.google.com/web/index.html#com.android.billingclient:billing
(direct link pattern: `https://dl.google.com/dl/android/maven2/com/android/billingclient/billing/<version>/billing-<version>.aar`).
Also add the AARs/JARs it depends on (listed in the same page's `.pom` file). Without it the coin
store stays hidden.

AAR files are ignored by git (see `.gitignore`).
