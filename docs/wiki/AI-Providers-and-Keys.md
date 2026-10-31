# AI providers and keys

| Provider | Cost | Notes |
|---|---|---|
| Ollama | Free | Runs on your own computer; nothing is sent online. |
| Gemini | Free tier available | Needs your own Google AI key. |
| Claude | Paid | Needs your own Anthropic key. |

Your key is stored in `config/villaging.json` on the computer or server running the world. Never share that file or post it in screenshots or issues. On a multiplayer server only the owner or an admin can change the key.

Messages you type and what a helper can see are sent to the provider you choose.

## Backup AI

If your main AI runs out of free usage, is overloaded or can't be reached, villagers can switch to a backup automatically. In `config/villaging.json` set `"fallbackProvider": "ollama"` (or `gemini` or `cloud`). It must be different from `provider`. A backup that needs a key uses the same `apiKey`. While the main AI is failing it rests for a minute, so your messages are not delayed. Leave `fallbackProvider` empty to turn this off.
