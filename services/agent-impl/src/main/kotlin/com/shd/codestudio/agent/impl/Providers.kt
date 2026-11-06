package com.shd.codestudio.agent.impl

import com.shd.codestudio.agent.LlmProvider
import com.shd.codestudio.agent.LlmProviderRegistry
import com.shd.codestudio.agent.SimpleLlmProviderRegistry

/** Assembles the built-in providers over a transport (OkHttp by default). Plugins may add more providers. */
object AgentProviders {
    fun defaults(transport: LlmTransport = OkHttpLlmTransport()): List<LlmProvider> =
        listOf(
            AnthropicProvider(transport),
            OpenAiProvider(transport),
            GeminiProvider(transport),
            OpenRouterProvider(transport),
        )

    fun registry(transport: LlmTransport = OkHttpLlmTransport()): LlmProviderRegistry =
        SimpleLlmProviderRegistry(defaults(transport))
}
