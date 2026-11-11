import express from "express";

const app = express();
app.use(express.json({ limit: "32kb" }));

const PORT = process.env.PORT || 3000;
const API_KEY = process.env.ELEVENLABS_API_KEY;
const VOICE_ID = "9SsFrOutdZkCkU5hIoQm";
const MODEL_ID = "eleven_flash_v2_5";

app.get("/health", (_req, res) => res.json({ ok: true, service: "mika-voice-proxy" }));

app.post("/v1/tts", async (req, res) => {
  try {
    if (!API_KEY) return res.status(503).json({ error: "Server voice key is not configured." });
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    if (!text) return res.status(400).json({ error: "Text is required." });
    if (text.length > 1200) return res.status(413).json({ error: "Text is too long." });

    const upstream = await fetch(
      `https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}?output_format=mp3_44100_128`,
      {
        method: "POST",
        headers: {
          "xi-api-key": API_KEY,
          "Content-Type": "application/json",
          "Accept": "audio/mpeg"
        },
        body: JSON.stringify({
          text,
          model_id: MODEL_ID,
          voice_settings: {
            stability: 0.48,
            similarity_boost: 0.78,
            style: 0.25,
            use_speaker_boost: true,
            speed: 1.04
          }
        })
      }
    );

    if (!upstream.ok) {
      const detail = await upstream.text();
      return res.status(upstream.status).json({ error: "ElevenLabs request failed.", detail: detail.slice(0, 500) });
    }

    res.setHeader("Content-Type", "audio/mpeg");
    res.setHeader("Cache-Control", "no-store");
    if (upstream.body) {
      const reader = upstream.body.getReader();
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        if (!res.write(Buffer.from(value))) await new Promise(resolve => res.once("drain", resolve));
      }
    }
    res.end();
  } catch (error) {
    console.error("TTS proxy error:", error);
    if (!res.headersSent) res.status(500).json({ error: "Voice service temporarily unavailable." });
    else res.end();
  }
});

app.listen(PORT, "0.0.0.0", () => console.log(`Mika voice proxy listening on ${PORT}`));
