# BouncyCastle registers its algorithms by class NAME and loads them by
# reflection when one is asked for. The shrinker sees no caller and drops
# them; SSH then fails at the key exchange ("X25519 KeyPairGenerator not
# available"). The provider and what it names stay.
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.pqc.jcajce.provider.** { *; }
-dontwarn org.bouncycastle.**
# sshj: its optional friends are not on Android.
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
