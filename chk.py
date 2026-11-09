from faster_whisper import WhisperModel
import glob, subprocess, numpy as np
m = WhisperModel("small", compute_type="int8")
for f in sorted(glob.glob("out/*.ogg")):
    raw = subprocess.run(["ffmpeg","-nostdin","-v","error","-i",f,"-f","f32le","-ac","1","-ar","16000","-"],capture_output=True).stdout
    a = np.frombuffer(raw, np.float32)
    segs, info = m.transcribe(a, language="ar", beam_size=1)
    print(f, "|", " ".join(s.text for s in segs)[:700], flush=True)
