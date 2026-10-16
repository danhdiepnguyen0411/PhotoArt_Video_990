package com.example.phortart_video_990

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phortart_video_990.core.sdk.ArtSdkManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CmsLegalLinksTest {

    private val TAG = "CmsLegalLinksTest"

    @Test
    fun testFetchLegalLinks() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ArtSdkManager.init(appContext)
        val client = ArtSdkManager.getClient()
        assert(client != null) { "ArtMagicClient should not be null" }

        // Test 1: getTemplateApps with category = APP_LEGAL_LINKS
        val deferredTemplates = CompletableDeferred<String>()
        client?.getTemplateApps(page = 0, size = 100, category = "APP_LEGAL_LINKS") { jsonElement, throwable ->
            if (throwable != null) {
                Log.e(TAG, "getTemplateApps APP_LEGAL_LINKS failed: ${throwable.message}", throwable)
                deferredTemplates.complete("ERROR: ${throwable.message}")
            } else {
                Log.i(TAG, "getTemplateApps APP_LEGAL_LINKS result: $jsonElement")
                deferredTemplates.complete(jsonElement.toString())
            }
        }
        val templatesJson = withTimeoutOrNull(15000L) { deferredTemplates.await() }
        println("===== CMS APP_LEGAL_LINKS RESULT =====")
        println(templatesJson)

        // Test 2: getDetailTemplate for APP_POLICIES_LINK
        val deferredPolicy = CompletableDeferred<String>()
        client?.getDetailTemplate("APP_POLICIES_LINK") { jsonElement, throwable ->
            if (throwable != null) {
                Log.e(TAG, "getDetailTemplate APP_POLICIES_LINK failed: ${throwable.message}", throwable)
                deferredPolicy.complete("ERROR: ${throwable.message}")
            } else {
                Log.i(TAG, "getDetailTemplate APP_POLICIES_LINK result: $jsonElement")
                deferredPolicy.complete(jsonElement.toString())
            }
        }
        val policyJson = withTimeoutOrNull(15000L) { deferredPolicy.await() }
        println("===== CMS APP_POLICIES_LINK DETAIL RESULT =====")
        println(policyJson)

        // Test 3: getDetailTemplate for APP_TERMS_LINK
        val deferredTerms = CompletableDeferred<String>()
        client?.getDetailTemplate("APP_TERMS_LINK") { jsonElement, throwable ->
            if (throwable != null) {
                Log.e(TAG, "getDetailTemplate APP_TERMS_LINK failed: ${throwable.message}", throwable)
                deferredTerms.complete("ERROR: ${throwable.message}")
            } else {
                Log.i(TAG, "getDetailTemplate APP_TERMS_LINK result: $jsonElement")
                deferredTerms.complete(jsonElement.toString())
            }
        }
        // Test 4: TemplateRepository.getTemplate("APP_POLICIES_LINK") and "APP_TERMS_LINK"
        val repository = com.example.phortart_video_990.data.repository.TemplateRepository()
        val policyTemplate = repository.getTemplate("APP_POLICIES_LINK")
        Log.i(TAG, "repository.getTemplate(APP_POLICIES_LINK) -> id=${policyTemplate?.id}, title=${policyTemplate?.title}, desc=${policyTemplate?.description}")

        val termsTemplate = repository.getTemplate("APP_TERMS_LINK")
        Log.i(TAG, "repository.getTemplate(APP_TERMS_LINK) -> id=${termsTemplate?.id}, title=${termsTemplate?.title}, desc=${termsTemplate?.description}")

        // Test 5: TemplateRepository.getLegalLinks(appContext)
        val legalLinks = repository.getLegalLinks(appContext)
        Log.i(TAG, "repository.getLegalLinks() -> policyUrl=${legalLinks.policyUrl}, termsUrl=${legalLinks.termsUrl}")
        assert(legalLinks.policyUrl.isNotBlank()) { "policyUrl must not be blank" }
        assert(legalLinks.termsUrl.isNotBlank()) { "termsUrl must not be blank" }

        val prefs = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(appContext)
        Log.i(TAG, "PreferencesManager cached -> policyUrl=${prefs.legalPolicyUrl}, termsUrl=${prefs.legalTermsUrl}")
        assert(prefs.legalPolicyUrl.isNotBlank())
        assert(prefs.legalTermsUrl.isNotBlank())
    }
}
