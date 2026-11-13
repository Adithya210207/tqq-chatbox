# TQQ Chatbox (JavaFX)

Chat app for the 5 fine-tuned Nakano sisters (`<Sister>_ryuu_v1` in Ollama).

## Run
- Double-click **`run.bat`** (it builds the app the first time).
- After changing any `.java` file: run **`build.bat`**, then `run.bat`.
- Needs: Java 26 (installed) + Ollama. The app starts Ollama itself if it isn't running.

## How it works (LoRA, not a system prompt)
| Part | What it does |
|---|---|
| Her reply | The app sends **no system prompt**. Each model already has its training prompt inside (Modelfile SYSTEM), so the app only sends the chat. |
| You | Always **Futaro** (the models were trained talking to him). |
| Memory | The last 20 turns of the **open chat thread** only. |
| Action line (line 1) | After she replies, the **same model** (already in memory) writes one short narrator line about what she does. Saved with the message, never sent back into her memory. Toggle: **Actions: on/off** in the chat header. |
| One sister at a time | Only one 8.5 GB model fits. Opening a sister unloads the others; going Home or closing the app frees her memory. |

## Folders
| Path | What |
|---|---|
| `src/tqq/` | Java code: `Main` = app shell, `HomeView` = 5-panel home, `ChatView` = chat screen, `Threads` = saved chats, `Ollama` = server calls, `Sister` = names/kanji/models/colours/greetings, `Ui` = icons + animations, `Cover` = image cropping |
| `assets/style.css` | All the looks ("Quintessence" theme). Edit it and restart the app; no rebuild needed. |
| `assets/images/<sister>.png` | Pictures (now placeholders). Replace with `ichika.png`, `nino.png`, `miku.png`, `yotsuba.png`, `itsuki.png`; `.jpg` also works. Restart the app. |
| `history/<sister>.json` | Each sister's chat threads |
| `lib/javafx-sdk-26.0.1/` | JavaFX (downloaded from gluonhq.com) |

## The .exe
| What | Where |
|---|---|
| The app | `TQQ Chatbox v1\TQQ Chatbox v1.exe` (double-click; Java is bundled inside, about 96 MB) |
| Shortcut | `TQQ Chatbox v1.lnk` in this folder. Copy it to the Desktop if you like |
| Rebuild it after code changes | Run `package.bat` (takes about a minute) |

- The exe uses **this folder's** `assets\` (pictures, style.css) and `history\` (chats). Picture and CSS changes show up without rebuilding.
- Keep the `TQQ Chatbox v1` folder here. If you move it somewhere else, it can't find the pictures and chats. Move the shortcut instead.
