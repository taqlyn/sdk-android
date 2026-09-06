package com.taqlyn.sample

import android.app.Application
import com.taqlyn.sdk.LinkProcessingMode
import com.taqlyn.sdk.SdkCore
import com.taqlyn.sdk.SdkOptions

/**
 * Proof harness — configures SdkCore early. Feature code imports SdkCore only.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SdkCore.configure(
            clientId = BuildConfig.TAQLYN_CLIENT_ID,
            publicKeyId = BuildConfig.TAQLYN_PUBLIC_KEY_ID,
            options =
                SdkOptions(
                    linkProcessingMode = LinkProcessingMode.ALL,
                    env = "sandbox",
                ),
            context = this,
        )
    }
}
