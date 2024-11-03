# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Retrofit classes and methods
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Keep Gson classes (if using Gson for JSON serialization)
-keep class com.google.gson.** { *; }

# Keep all classes in the 'roomdb' package
-keep class com.jaimefutter.walletmaven.roomdb.** { *; }

# Keep all classes in the 'ui' package and sub-packages (budgetoverview and dashboard)
-keep class com.jaimefutter.walletmaven.ui.** { *; }

# Keep MainActivity and SettingsActivity
-keep class com.jaimefutter.walletmaven.MainActivity { *; }
-keep class com.jaimefutter.walletmaven.SettingsActivity { *; }

# Keep Firebase Messaging service
-keep class com.jaimefutter.walletmaven.MyFirebaseMessagingService { *; }

# Keep custom Retrofit client
-keep class com.jaimefutter.walletmaven.RetrofitClient { *; }
-keep class com.jaimefutter.walletmaven.WalletMavenAPIService { *; }

# Keep BiometricManager class
-keep class com.jaimefutter.walletmaven.MyBiometricManager { *; }

# Keep data classes like Expense
-keep class com.jaimefutter.walletmaven.ExpenseResponse { *; }
-keep class com.jaimefutter.walletmaven.ExpenseEntity { *; }
-keep class com.jaimefutter.walletmaven.CategoryResponse { *; }
-keep class com.jaimefutter.walletmaven.CategoryEntity { *; }

# Keep attributes used for Retrofit's serialization and Room entities
-keepattributes Signature
-keepattributes *Annotation*

# Suppress warnings for specific classes (already in your file)
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
