package com.careerplatform.ai.client;

/**
 * Provider-neutral boundary for typed chat completions.
 *
 * <p>Callers own the system instruction and user content. The gateway only
 * sends those values to the configured chat client and converts the response
 * to the requested type.</p>
 */
public interface AiChatGateway {

    /**
     * Generate a structured response from the configured chat provider.
     *
     * @param systemInstruction system-level instruction for the model
     * @param userContent user content, including any caller-owned trust
     *                    boundaries
     * @param responseType target response type
     * @param <T> target response type
     * @return parsed response entity
     * @throws com.careerplatform.ai.exception.AiServiceUnavailableException
     *         when AI is disabled or no provider is available
     * @throws com.careerplatform.ai.exception.AiProviderException when the
     *         provider request fails
     * @throws com.careerplatform.ai.exception.AiInvalidResponseException when
     *         the provider response cannot be converted to the target type
     */
    <T> T generateStructured(String systemInstruction, String userContent, Class<T> responseType);
}
