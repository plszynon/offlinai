package com.offlinai.app.data

data class Featured(val title: String, val repo: String, val file: String, val gb: Double)

object Catalog {
    // Starter picks (Q4_K_M quantization). If a link ever breaks, search Hugging Face in the app instead.
    val featured = listOf(
        Featured("Qwen3 0.6B", "unsloth/Qwen3-0.6B-GGUF", "Qwen3-0.6B-Q4_K_M.gguf", 0.4),
        Featured("Qwen3 1.7B", "unsloth/Qwen3-1.7B-GGUF", "Qwen3-1.7B-Q4_K_M.gguf", 1.1),
        Featured("Qwen3 4B", "unsloth/Qwen3-4B-GGUF", "Qwen3-4B-Q4_K_M.gguf", 2.5),
        Featured("Qwen2.5 0.5B Instruct", "Qwen/Qwen2.5-0.5B-Instruct-GGUF", "qwen2.5-0.5b-instruct-q4_k_m.gguf", 0.4),
        Featured("Qwen2.5 1.5B Instruct", "Qwen/Qwen2.5-1.5B-Instruct-GGUF", "qwen2.5-1.5b-instruct-q4_k_m.gguf", 1.0),
        Featured("Qwen2.5 3B Instruct", "Qwen/Qwen2.5-3B-Instruct-GGUF", "qwen2.5-3b-instruct-q4_k_m.gguf", 1.9),
        Featured("Llama 3.2 1B Instruct", "bartowski/Llama-3.2-1B-Instruct-GGUF", "Llama-3.2-1B-Instruct-Q4_K_M.gguf", 0.8),
        Featured("Llama 3.2 3B Instruct", "bartowski/Llama-3.2-3B-Instruct-GGUF", "Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2.0),
        Featured("Gemma 3 1B", "unsloth/gemma-3-1b-it-GGUF", "gemma-3-1b-it-Q4_K_M.gguf", 0.8),
        Featured("Gemma 3 4B", "unsloth/gemma-3-4b-it-GGUF", "gemma-3-4b-it-Q4_K_M.gguf", 2.5),
        Featured("Gemma 2 2B", "bartowski/gemma-2-2b-it-GGUF", "gemma-2-2b-it-Q4_K_M.gguf", 1.6),
        Featured("Phi 3.5 mini", "bartowski/Phi-3.5-mini-instruct-GGUF", "Phi-3.5-mini-instruct-Q4_K_M.gguf", 2.4),
        Featured("SmolLM2 1.7B", "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF", "smollm2-1.7b-instruct-q4_k_m.gguf", 1.1),
        Featured("TinyLlama 1.1B", "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF", "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", 0.7),
        Featured("Mistral 7B v0.3 (big)", "bartowski/Mistral-7B-Instruct-v0.3-GGUF", "Mistral-7B-Instruct-v0.3-Q4_K_M.gguf", 4.4),
        Featured("Llama 3.1 8B (big)", "bartowski/Meta-Llama-3.1-8B-Instruct-GGUF", "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf", 4.9)
    )
}
