# Dedicated GPU VM deployment

This directory deploys ABAP Guardian, the existing ABAP Expert RAG service,
Ollama and Caddy on one NVIDIA GPU VM. Only ports 80/443 are public. Ollama,
Chroma and the ABAP Agent remain on an isolated Docker network. Caddy exposes
the Guardian API with bearer-token authentication and the existing browser UI
with separate Basic Authentication.

Follow the complete, copy-and-paste tutorial in
[`../../docs/dedicated-gpu-vm.md`](../../docs/dedicated-gpu-vm.md).

Do not commit `.env` or `secrets/`; both are ignored by Git.
