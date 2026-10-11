#!/usr/bin/env python3
"""Builds the images and the walkthrough video of tutorial 25 (JOSE: JWE and JWK).

Input is the output of TutorialJoseCaptureTool:

    mvn -o -q test -Dtest=TutorialJoseCaptureTool -DtutorialCaptureOut=target/tutorial-25
    python3 scripts/build_tutorial_25_media.py target/tutorial-25

Writes tutoriales/imagenes/cryptocarver-25-<id>.jpeg for every captured step and
tutoriales/videos/25-jose-jwe-y-jwk.mp4. Needs Pillow and ffmpeg (libx264).
"""
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMAGES = ROOT / "tutoriales" / "imagenes"
VIDEO = ROOT / "tutoriales" / "videos" / "25-jose-jwe-y-jwk.mp4"
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"

IMAGE_WIDTH = 1600
FRAME = (1920, 1080)
CAPTION_HEIGHT = 132
NAVY = (27, 46, 75)
BLUE = (15, 123, 168)
WHITE = (255, 255, 255)
MUTED = (176, 196, 214)

# Section cards shown before the first step whose id starts with the prefix.
SECTIONS = [
    ("jwk-", "JWK (Keys)", "Convertir claves, thumbprint, metadatos, JWKS y generación de pares"),
    ("jwe-", "JWE (Encrypted)", "Cifrar y descifrar: RSA-OAEP, AES-KW, dir, PBES2, ECDH-ES, AAD y varios destinatarios"),
    ("jwt-", "JWT (Signed) y JWA", "Firmar, validar y consultar el registro de algoritmos"),
]


def font(path, size):
    return ImageFont.truetype(path, size)


def wrap(draw, text, face, width):
    lines, line = [], ""
    for word in text.split():
        candidate = (line + " " + word).strip()
        if draw.textlength(candidate, font=face) <= width or not line:
            line = candidate
        else:
            lines.append(line)
            line = word
    return lines + [line] if line else lines


def card(title, subtitle, kicker="CRYPTOCARVER / TUTORIAL 25"):
    image = Image.new("RGB", FRAME, NAVY)
    draw = ImageDraw.Draw(image)
    draw.text((140, 380), kicker, font=font(FONT_BOLD, 30), fill=BLUE)
    draw.text((140, 430), title, font=font(FONT_BOLD, 84), fill=WHITE)
    y = 560
    for line in wrap(draw, subtitle, font(FONT, 40), FRAME[0] - 280):
        draw.text((140, y), line, font=font(FONT, 40), fill=MUTED)
        y += 54
    return image


def step_frame(shot, caption, index, total):
    image = Image.new("RGB", FRAME, NAVY)
    area = (FRAME[0], FRAME[1] - CAPTION_HEIGHT)
    scale = min(area[0] / shot.width, area[1] / shot.height)
    scaled = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    image.paste(scaled, ((area[0] - scaled.width) // 2, (area[1] - scaled.height) // 2))
    draw = ImageDraw.Draw(image)
    counter = f"{index}/{total}"
    draw.text((48, area[1] + 46), counter, font=font(FONT_BOLD, 34), fill=BLUE)
    face = font(FONT, 36)
    lines = wrap(draw, caption, face, FRAME[0] - 280)[:2]
    y = area[1] + (CAPTION_HEIGHT - 46 * len(lines)) // 2
    for line in lines:
        draw.text((200, y), line, font=face, fill=WHITE)
        y += 46
    return image


def main():
    source = Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / "target" / "tutorial-25")
    steps = [line.split("\t") for line in (source / "manifest.tsv").read_text(encoding="utf-8").splitlines()]
    IMAGES.mkdir(parents=True, exist_ok=True)
    VIDEO.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as work:
        work = Path(work)
        frames = []  # (file, seconds)

        def add(image, seconds):
            path = work / f"frame-{len(frames):03d}.png"
            image.save(path)
            frames.append((path, seconds))

        add(card("JOSE a fondo: JWE y JWK", "Recorrido paso a paso por la sección JOSE de CryptoCarver"), 4)
        pending = list(SECTIONS)
        for index, (file, step_id, caption) in enumerate(steps, start=1):
            shot = Image.open(source / file).convert("RGB")
            height = round(shot.height * IMAGE_WIDTH / shot.width)
            shot.resize((IMAGE_WIDTH, height), Image.LANCZOS).save(
                IMAGES / f"cryptocarver-25-{step_id}.jpeg", quality=86, optimize=True)
            if pending and step_id.startswith(pending[0][0]):
                _, title, subtitle = pending.pop(0)
                add(card(title, subtitle), 3)
            add(step_frame(shot, caption, index, len(steps)), max(5, round(len(caption) / 13)))

        playlist = work / "frames.txt"
        with playlist.open("w", encoding="utf-8") as listing:
            for path, seconds in frames:
                listing.write(f"file '{path}'\nduration {seconds}\n")
            listing.write(f"file '{frames[-1][0]}'\n")  # the concat demuxer drops the last duration otherwise
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(playlist),
                        "-vf", "fps=30,format=yuv420p", "-c:v", "libx264", "-preset", "slow", "-crf", "20",
                        "-movflags", "+faststart", str(VIDEO)], check=True)
        total = sum(seconds for _, seconds in frames)
    print(f"{len(steps)} images -> {IMAGES}")
    print(f"video {total // 60}:{total % 60:02d} -> {VIDEO}")


if __name__ == "__main__":
    main()
