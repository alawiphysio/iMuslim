from faster_whisper import WhisperModel
import glob
m = WhisperModel("small", compute_type="int8")
for f in sorted(glob.glob("out/*.ogg")):
    segs, info = m.transcribe(f, language="ar", beam_size=1)
    print(f, "|", " ".join(s.text for s in segs)[:700], flush=True)
