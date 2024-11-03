package com.jaimefutter.walletmaven

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class MyBiometricManager private constructor(){
    private var executor: Executor? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private var context: Context? = null
    private var fragmentActivity: FragmentActivity? = null
    private var callback: Callback? = null
    private val TAG = "BiometricManager"

    companion object{
        private var instance: MyBiometricManager? = null
        const val REQUEST_CODE = 100
        fun getInstance(context: Context): MyBiometricManager?{
            if(instance == null){
                instance = MyBiometricManager()
            }
            instance!!.init(context)
            return instance
        }
    }

    private fun init(context: Context){
        this.context = context
        fragmentActivity = context as FragmentActivity
        callback = context as Callback
    }

    fun checkIfBiometricFeatureAvailable(): Boolean{
        val biometricManager = BiometricManager.from(
            context!!
        )
        when(biometricManager.canAuthenticate()){
            BiometricManager.BIOMETRIC_SUCCESS ->{
                Log.d("MY_APP_TAG",
                    "App can authenticate using biometrics.")
                return true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.e("MY_APP_TAG", "No biometric features available on this device")
                Toast.makeText(
                    context,
                    "No biometric features available on this device",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.e("MY_APP_TAG", "Biometric features are " +
                        "currently unavailable.")
                Toast.makeText(
                    context,
                    "Biometric features are currently unavailable.",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                enrollIntent.putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    android.hardware.biometrics.
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager
                                .Authenticators.DEVICE_CREDENTIAL
                )
                fragmentActivity!!.startActivityForResult(enrollIntent, REQUEST_CODE)
                return false
            }
        }
        return false
    }

    fun authenticate(){
        setupBiometric()
        biometricPrompt!!.authenticate(promptInfo!!)
    }

    fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(context!!)
        biometricPrompt = BiometricPrompt(
            fragmentActivity!!, executor!!,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    // Handle only user-initiated bypass or cancellation errors
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        Log.e(TAG, "User bypassed authentication. Logging out.")
                        callback!!.onBiometricAuthenticationResult(Callback.AUTHENTICATION_ERROR, errString)
                    } else {
                        Log.e(TAG, "Authentication error: $errString")
                        // You can handle other errors here without logging out, or just log the error
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    callback!!.onBiometricAuthenticationResult(
                        Callback.AUTHENTICATION_SUCCESSFUL,
                        ""
                    )
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    callback!!.onBiometricAuthenticationResult(Callback.AUTHENTICATION_FAILED, "")
                }
            }
        )
        showBiometricPrompt()
    }
    private fun showBiometricPrompt(){
        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for WalletMaven")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use account password")
            .build()
    }

    internal interface Callback{
        fun onBiometricAuthenticationResult(result:String?, errString: CharSequence?)

        companion object{
            const val AUTHENTICATION_SUCCESSFUL = "AUTHENTICATION_SUCCESSFUL"
            const val AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED"
            const val AUTHENTICATION_ERROR = " AUTHENTICATION_ERROR"
        }

    }
}