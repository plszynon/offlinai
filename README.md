# offlinai

A fully offline AI chat app for Android. The model runs inside the app with llama.cpp. No Termux, no server.

## Use
1. Menu, then Model library. Install a small model (1B to 3B works best on most phones).
2. It is selected automatically. Chat.
3. Search Hugging Face in the library for any GGUF model, or paste a direct .gguf link.

Downloading a model needs internet once. After that everything works offline.

## Notes
- CPU only. Larger models (7B+) need a lot of RAM and may crash on some phones.
- Chat formatting is picked from the model's file name (ChatML, Llama 3, Gemma, Mistral, Phi 3, Zephyr).
- Engine: Kotlin-LlamaCpp (io.github.ljcamargo:llamacpp-kotlin).
