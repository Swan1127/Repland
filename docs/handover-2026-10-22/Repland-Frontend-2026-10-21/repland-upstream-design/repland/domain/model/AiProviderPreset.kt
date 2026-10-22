package com.swan1127.repland.domain.model

/**
 * A starting point for [AiProviderConfig].
 *
 * Only the base URL and a display name are baked in. **Model names are deliberately not
 * hard-coded.** Providers rename and retire them constantly — DeepSeek's documented
 * `deepseek-chat` was retired in favour of `deepseek-flash`, and Zhipu is now on
 * `glm-5.3` — and a stale name fails at request time with an opaque provider error that
 * the user cannot diagnose. The model list is fetched from `GET {baseUrl}/models`
 * instead, which every provider listed here exposes.
 *
 * Every base URL below was read off that provider's own documentation on 2026-10-06;
 * see `docs/竞品对标与打磨计划.md` §4.2. They are full request prefixes, not hosts:
 * the client appends `/chat/completions` directly, and
 * `SecureAiProviderConfigRepository.normalizeBaseUrl` only trims whitespace and a
 * trailing slash — it never inserts a missing `/v1`.
 */
enum class AiProviderPreset(
    val displayName: String,
    val baseUrl: String,
) {
    /** The provider this project was originally wired for. */
    AGNES("Agnes", DEFAULT_AGNES_BASE_URL),

    /**
     * The same provider's mainland endpoint. It is a separate entry rather than an alias
     * because the two addresses are not interchangeable — a key issued for one is rejected
     * by the other — and because the settings dialog has always offered both, so dropping
     * one to a free-form field would be a regression.
     */
    AGNES_CN("Agnes 中国", DEFAULT_AGNES_CN_BASE_URL),

    /** <https://api-docs.deepseek.com> — OpenAI-compatible. Note the absence of `/v1`. */
    DEEPSEEK("DeepSeek 深度求索", "https://api.deepseek.com"),

    /** <https://docs.bigmodel.cn> — OpenAI-compatible. */
    ZHIPU_GLM("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),

    /** <https://platform.kimi.com> — OpenAI-compatible. */
    MOONSHOT_KIMI("Kimi 月之暗面", "https://api.moonshot.cn/v1"),

    /**
     * Alibaba Bailian. This is the legacy domain, which the documentation states remains
     * usable. Newer workspaces are addressed as
     * `https://{WorkspaceId}.{region}.maas.aliyuncs.com/compatible-mode/v1`, so a user on
     * one of those must edit the field by hand.
     */
    DASHSCOPE_QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),

    OPENAI("OpenAI", "https://api.openai.com/v1"),

    /** <https://console.groq.com> — OpenAI-compatible under `/openai/v1`. */
    GROQ("Groq", "https://api.groq.com/openai/v1"),

    /** <https://openrouter.ai> — OpenAI-compatible; also exposes `GET /api/v1/models`. */
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),

    /** Free-form: the user supplies the address themselves. */
    CUSTOM("自定义", ""),
    ;

    companion object {
        /** Every preset that actually carries an address. */
        val configured: List<AiProviderPreset> get() = entries.filter { it != CUSTOM }

        /**
         * The preset whose [baseUrl] matches [baseUrl], or [CUSTOM] when nothing does —
         * which includes the empty string and an unrecognised address.
         *
         * A single trailing slash is ignored because
         * `SecureAiProviderConfigRepository.normalizeBaseUrl` strips it before storing, so
         * a round-tripped value would otherwise stop matching its own preset. Nothing else
         * is normalised: a URL path is case-sensitive, and guessing at near-misses would
         * silently rewrite the user's address.
         */
        fun matching(baseUrl: String): AiProviderPreset {
            val normalized = baseUrl.trim().trimEnd('/')
            return configured.firstOrNull { it.baseUrl.trimEnd('/') == normalized } ?: CUSTOM
        }
    }
}
