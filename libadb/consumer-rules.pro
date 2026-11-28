# Conscrypt and BouncyCastle are looked up reflectively / by provider name.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
