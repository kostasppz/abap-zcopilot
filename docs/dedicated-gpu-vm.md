# Dedicated GPU VM: complete deployment and Eclipse tutorial

This guide moves the complete ABAP AI stack away from the developer PC. The
GPU VM supplies the GPU, RAM, CPU and storage; Eclipse sends requests through
HTTPS and does not require local Docker or Ollama.

The instructions use a **DigitalOcean GPU Droplet with its AI/ML-ready Ubuntu
image** as the concrete example because Docker and the NVIDIA container
runtime are preconfigured. The same repository deployment works on another
Ubuntu NVIDIA GPU VM when Docker Engine, Docker Compose and NVIDIA Container
Toolkit are installed.

Official references:

- [DigitalOcean GPU Droplets](https://docs.digitalocean.com/products/gpu-droplets/)
- [DigitalOcean recommended GPU setup](https://docs.digitalocean.com/products/droplets/getting-started/recommended-gpu-setup/)
- [Docker Compose installation](https://docs.docker.com/compose/install/linux/)
- [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)

## Resulting architecture

```text
Eclipse/ADT
  -> https://guardian.example.com (TLS + bearer token)
  -> Caddy reverse proxy
  -> ABAP Guardian gateway + deterministic analyzer
  -> ABAP Expert RAG service + Chroma
  -> Ollama on the NVIDIA GPU

Browser
  -> https://assistant.example.com (TLS + Basic Authentication)
  -> existing ABAP Expert web chat
```

Only TCP ports 80 and 443 are public. Ports 8000 and 11434 are not published.
ABAP source is not written to the VM by Guardian, but it is processed in VM
memory. Treat the VM as a system that handles source code and apply your
organization's SAP, privacy and information-security rules.

## Step 1: check the model on the current PC

Before renting hardware, find the model name and size:

```powershell
docker exec abap-ollama ollama list
docker exec abap-ollama ollama show abap-expert
```

Use the quantized model size to choose GPU memory:

| Model class | Practical starting point |
| --- | --- |
| 7–8B Q4 | 12–16 GB GPU memory, 16 GB system RAM |
| 13–14B Q4 | 16–24 GB GPU memory, 32 GB system RAM |
| 30–32B Q4 | 24–48 GB GPU memory, 64 GB system RAM |
| 70B | 48–80+ GB GPU memory, 96+ GB system RAM |

Allow at least 100 GB disk space for the operating system, Docker images,
Ollama models, knowledge documents, Chroma data and backups. These are
starting points; the exact requirement depends on the base model in
`Modelfile`, context size and concurrent users.

## Step 2: prepare the required accounts and names

You need:

1. A GPU-cloud account with billing enabled.
2. An SSH key on the Windows PC.
3. Two subdomains such as `guardian.example.com` and
   `assistant.example.com`.
4. The current local `abap-agent` directory.
5. Access to `https://github.com/kostasppz/abap-zcopilot`.

Create an SSH key in Windows PowerShell if one does not already exist:

```powershell
ssh-keygen -t ed25519 -C "abap-guardian-vm"
Get-Content "$HOME\.ssh\id_ed25519.pub"
```

Add only the `.pub` value to the cloud provider. Never upload or send the
private `id_ed25519` file.

## Step 3: create the GPU VM

In DigitalOcean:

1. Open **Create → GPU Droplet**.
2. Select the **AI/ML-ready** image.
3. Select one NVIDIA GPU with enough GPU memory for Step 1.
4. Choose a region approved for the project's data. For SAP source from a
   German organization, confirm the permitted data location internally.
5. Choose sufficient SSD storage, starting at 100 GB.
6. Add the SSH public key created in Step 2.
7. Enable provider backups if permitted and required.
8. Create the Droplet and record its public IP address.

The VM incurs charges while provisioned. Provider billing and stop/delete
behavior differ, so configure a budget alert before continuing.

## Step 4: configure DNS

At the DNS provider, create two `A` records:

| Type | Name | Value |
| --- | --- | --- |
| A | `guardian` | `<GPU_VM_PUBLIC_IP>` |
| A | `assistant` | `<GPU_VM_PUBLIC_IP>` |

Wait until the address resolves from the Windows PC:

```powershell
Resolve-DnsName guardian.example.com
Resolve-DnsName assistant.example.com
```

Caddy cannot obtain the TLS certificate until DNS points to the VM and ports
80/443 are reachable.

## Step 5: configure cloud and VM firewalls

Create a cloud firewall with these inbound rules:

| Protocol/port | Source |
| --- | --- |
| TCP 22 | Your office/VPN public IP only |
| TCP 80 | All IPv4/IPv6 addresses |
| TCP 443 | All IPv4/IPv6 addresses |
| UDP 443 | All IPv4/IPv6 addresses (optional HTTP/3) |

Do **not** open 8000, 8001 or 11434.

Connect to the VM. Replace `root` with the provider's configured user if
necessary:

```powershell
ssh root@<GPU_VM_PUBLIC_IP>
```

Configure the Ubuntu firewall after allowing SSH:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
sudo ufw status verbose
```

Keep the current SSH session open until a second SSH connection succeeds.

## Step 6: verify GPU and container support

Run on the VM:

```bash
nvidia-smi
docker --version
docker compose version
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

All four commands must succeed. When using a different Ubuntu GPU VM, install
Docker Engine, Docker Compose and NVIDIA Container Toolkit from the official
links at the top of this document before proceeding.

Install the remaining utilities and prepare a non-root deployment directory:

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates openssl
sudo mkdir -p /opt/abap-stack
sudo chown -R "$(id -un):$(id -gn)" /opt/abap-stack
```

## Step 7: transfer the existing ABAP Agent

The agent contains the private knowledge and the custom `Modelfile`; it should
not be committed to the public Guardian repository.

On the Windows PC, from the directory that contains `abap-agent`:

```powershell
tar --exclude=.venv --exclude=vector_db --exclude=__pycache__ -czf abap-agent-cloud.tar.gz abap-agent
scp .\abap-agent-cloud.tar.gz root@<GPU_VM_PUBLIC_IP>:/tmp/
```

On the VM:

```bash
cd /opt/abap-stack
tar -xzf /tmp/abap-agent-cloud.tar.gz
rm /tmp/abap-agent-cloud.tar.gz
mkdir -p /opt/abap-stack/abap-agent/knowledge
mkdir -p /opt/abap-stack/abap-agent/knowledge/uploads
mkdir -p /opt/abap-stack/abap-agent/vector_db
```

Confirm that these files/directories exist:

```bash
ls -la /opt/abap-stack/abap-agent
```

Required content:

```text
agent.py
Dockerfile
requirements.txt (or the requirements file used by Dockerfile)
Modelfile
knowledge/
templates/
static/
vector_db/
```

The vector database was deliberately excluded from transfer and will be
rebuilt from `knowledge/` on the VM.

## Step 8: clone and configure ABAP Guardian

On the VM:

```bash
cd /opt/abap-stack
git clone https://github.com/kostasppz/abap-zcopilot.git
cd /opt/abap-stack/abap-zcopilot/deploy/gpu-vm
cp .env.example .env
nano .env
```

At minimum, replace:

```dotenv
GUARDIAN_DOMAIN=guardian.example.com
CHAT_DOMAIN=assistant.example.com
ABAP_AGENT_DIR=/opt/abap-stack/abap-agent
CHAT_MODEL=abap-expert
EMBEDDING_MODEL=embeddinggemma
```

Create a random API token. It is mounted into Guardian as a Docker secret and
must never be committed, posted in an issue, or pasted into a chat:

```bash
mkdir -m 700 -p secrets
umask 077
openssl rand -hex 32 > secrets/guardian_api_token
chmod 600 secrets/guardian_api_token
```

Store a copy in an approved password manager. It will be entered into Eclipse
in Step 12.

Create a different password for the browser chat. Caddy accepts only a hash,
never the plaintext password. Run this command and enter the password when
prompted:

```bash
docker run --rm -it caddy:2-alpine \
  caddy hash-password --algorithm bcrypt
```

Copy the resulting hash, then create the Caddy authentication snippet:

```bash
nano secrets/chat_auth
```

Enter the following, replacing `<BCRYPT_HASH>` with the generated value and
`kostas` with the desired username:

```caddyfile
basic_auth {
    kostas <BCRYPT_HASH>
}
```

Protect the file and store the plaintext browser password only in the password
manager:

```bash
chmod 600 secrets/chat_auth
```

Validate the configuration without starting containers:

```bash
docker compose --env-file .env config --quiet
```

## Step 9: build and start the complete service

```bash
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
```

The first start downloads the embedding/base models and creates
`abap-expert`; this can take several minutes. Monitor it:

```bash
docker compose --env-file .env logs -f model-loader
```

Exit log following with `Ctrl+C`; the containers continue running.

If the model loader fails, inspect Ollama and retry:

```bash
docker compose --env-file .env logs --tail=200 ollama model-loader
docker compose --env-file .env run --rm model-loader
docker compose --env-file .env up -d
```

## Step 10: build the remote knowledge index

After `abap-ai` is healthy:

```bash
docker compose --env-file .env exec abap-ai python agent.py index
```

Re-run this command whenever files in `abap-agent/knowledge/` change. Indexing
creates embeddings; it does not train or modify the language model.

## Step 11: verify HTTPS, authentication and AI

Public health does not expose source or secrets:

```bash
curl --fail --silent --show-error "https://guardian.example.com/health"
```

The result should include:

```json
{
  "status": "ok",
  "llmAvailable": true,
  "llmProvider": "abap-agent",
  "analyzerAvailable": true,
  "authenticationRequired": true,
  "authenticationConfigured": true
}
```

An unauthenticated API call must fail with HTTP 401:

```bash
curl -i "https://guardian.example.com/api/v1/models"
```

Test an authenticated request on the VM without printing the token:

```bash
TOKEN="$(cat secrets/guardian_api_token)"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${TOKEN}" \
  "https://guardian.example.com/api/v1/models"
unset TOKEN
```

For a final chat test, use a harmless synthetic question instead of real SAP
source:

```bash
TOKEN="$(cat secrets/guardian_api_token)"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"question":"Show a small ABAP example using a hashed internal table."}' \
  "https://guardian.example.com/api/v1/chat"
unset TOKEN
```

Open `https://assistant.example.com` in a browser. Caddy asks for the username
and browser password created in Step 8, then displays the existing ABAP Expert
web interface. Browser authentication and the Eclipse API token are separate;
do not reuse the same secret.

## Step 12: update and configure Eclipse

API-token support is included in ABAP Guardian 0.5.0 or newer.

1. In Eclipse, open **Help → Check for Updates**.
2. Select the ABAP Guardian update and restart Eclipse.
3. If Eclipse does not find it, open **Help → Install New Software…**.
4. Use `https://kostasppz.github.io/abap-zcopilot/` in **Work with**.
5. Select **ABAP Guardian**, complete the installation and restart.
6. Open **Window → Preferences → ABAP Guardian**.
7. Set **Service URL** to `https://guardian.example.com` without a trailing
   `/api` path.
8. Set **Request timeout** to `180` seconds for the first test.
9. Paste the value from `secrets/guardian_api_token` into **API token**.
10. Select **Store securely**. The token is encrypted in Eclipse Secure
    Storage, not ordinary workspace preferences.
11. Enable **Use online AI enhancement**.
12. Select **Test Connection**, then **Apply and Close**.

Do not paste the Ollama URL, port 11434 or the ABAP Agent port into Eclipse.
Eclipse always connects to the public Guardian HTTPS address.

## Step 13: use it in Eclipse

1. Open an ABAP object in ADT.
2. Press `Ctrl+Alt+C` to open **ABAP Guardian Copilot**.
3. Enter a question and select **Ask**.
4. Select ABAP code and use **Explain selection** or **Suggest correction**.
5. Press `Ctrl+Alt+G` for deterministic analysis of the active object.
6. Review findings and corrections; Guardian shows a diff and never changes
   ABAP source without confirmation.

Optional automatic analysis is under **Window → Preferences → ABAP
Guardian**. Start with manual analysis. Automatic AI analysis consumes GPU
time and sends source after each configured typing pause.

## Step 14: enable Eclipse update notifications

Open **Window → Preferences → Install/Update → Automatic Updates** and enable
automatic update checking. Eclipse can notify or download an update, but a
new plug-in version appears only after a new GitHub release publishes the p2
site. If that preference page is not installed in the Eclipse package, use
**Help → Check for Updates**.

## Step 15: deploy repository updates

After a new Guardian version is merged on GitHub, run on the VM:

```bash
cd /opt/abap-stack/abap-zcopilot
git pull --ff-only
cd deploy/gpu-vm
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
```

This rebuilds Guardian while preserving Ollama models, Caddy certificates,
knowledge documents and the Chroma database.

When the local ABAP Agent source changes, transfer the changed source files to
`/opt/abap-stack/abap-agent` and run the same Compose command. When knowledge
changes, also repeat Step 10.

## Step 16: logs and troubleshooting

```bash
cd /opt/abap-stack/abap-zcopilot/deploy/gpu-vm
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200 caddy abap-guardian abap-ai ollama
docker compose --env-file .env exec ollama ollama list
nvidia-smi
```

Common causes:

| Symptom | Check |
| --- | --- |
| Caddy cannot obtain a certificate | Both DNS names, ports 80/443 and firewall |
| Eclipse receives HTTP 401 | Token in Eclipse does not match the secret file |
| `llmAvailable` is false | `model-loader`, exact model names and Ollama logs |
| Out of GPU memory | Smaller quantization/model or larger-GPU VM |
| Chat times out | Increase Eclipse and `AI_TIMEOUT_SECONDS`, then inspect GPU utilization |
| New documents are ignored | Rebuild the knowledge index |

Never troubleshoot by publishing ports 8000 or 11434.

## Step 17: backups and recovery

Back up the private knowledge and vector database:

```bash
sudo tar -czf "/opt/abap-stack/abap-agent-backup-$(date +%F).tar.gz" \
  -C /opt/abap-stack/abap-agent knowledge vector_db Modelfile
```

Use encrypted provider snapshots or an approved encrypted backup destination.
The Ollama named volume can be recreated by the model loader, but preserving
it avoids downloading the models again.

## Step 18: rotate a compromised API token

On the VM:

```bash
cd /opt/abap-stack/abap-zcopilot/deploy/gpu-vm
umask 077
openssl rand -hex 32 > secrets/guardian_api_token
docker compose --env-file .env up -d --force-recreate abap-guardian
```

Replace the stored API token in every authorized Eclipse installation. Old
tokens stop working as soon as the Guardian container is recreated.

To rotate the browser password, repeat the Caddy `hash-password` command,
replace the hash in `secrets/chat_auth`, and recreate Caddy:

```bash
docker compose --env-file .env up -d --force-recreate caddy
```

## Production checklist

- Confirm the VM region and provider are approved for SAP source code.
- Keep SSH restricted to an office/VPN address and use SSH keys only.
- Publish only 80/443; never publish Ollama, Chroma or the ABAP Agent.
- Store the Guardian token in an approved password manager and Eclipse Secure
  Storage.
- Use a separate strong browser-chat password; only its hash belongs on the
  VM.
- Enable provider budget alerts, backups, monitoring and security updates.
- Review who can upload knowledge documents and rebuild the index.
- Test restore and token rotation before wider rollout.
- Do not treat AI answers or fixes as validated SAP code.
