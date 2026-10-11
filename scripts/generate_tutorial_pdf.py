#!/usr/bin/env python3
"""Renders a tutorial Markdown file as a PDF in the layout of tutoriales/pdfs.

    python3 scripts/generate_tutorial_pdf.py tutoriales/25-jose-jwe-y-jwk-referencia-completa.md

The output goes to tutoriales/pdfs/<name>.pdf unless a second argument names it. Needs
ReportLab. Supports the Markdown the tutorials use: headings, paragraphs, ordered and
unordered lists, tables, fenced and indented code, block quotes, images and inline
bold, italic, code and links.
"""
import html
import re
import sys
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (Image, KeepTogether, PageBreak, Paragraph, Preformatted, SimpleDocTemplate,
                                Spacer, Table, TableStyle)

FONTS = Path("/System/Library/Fonts/Supplemental")
NAVY = colors.HexColor("#1B2E4B")
BLUE = colors.HexColor("#0F7BA8")
INK = colors.HexColor("#2B3A4A")
MUTED = colors.HexColor("#6B7C8F")
RULE = colors.HexColor("#C9D6E2")
TINT = colors.HexColor("#F3F7FA")
MARGIN = 57
WIDTH = A4[0] - 2 * MARGIN

for name, file in (("Body", "Arial.ttf"), ("Body-Bold", "Arial Bold.ttf"), ("Body-Italic", "Arial Italic.ttf")):
    pdfmetrics.registerFont(TTFont(name, str(FONTS / file)))
for name, face in (("Mono", 0), ("Mono-Bold", 1)):
    pdfmetrics.registerFont(TTFont(name, "/System/Library/Fonts/Menlo.ttc", subfontIndex=face))
pdfmetrics.registerFontFamily("Body", normal="Body", bold="Body-Bold", italic="Body-Italic", boldItalic="Body-Bold")
pdfmetrics.registerFontFamily("Mono", normal="Mono", bold="Mono-Bold", italic="Mono", boldItalic="Mono-Bold")


def style(name, **kwargs):
    base = dict(fontName="Body", fontSize=10.5, leading=15, textColor=INK, spaceAfter=7)
    base.update(kwargs)
    return ParagraphStyle(name, **base)


BODY = style("body")
KICKER = style("kicker", fontName="Body-Bold", fontSize=9.5, textColor=BLUE, spaceAfter=10)
TITLE = style("title", fontName="Body-Bold", fontSize=25, leading=30, textColor=NAVY, spaceAfter=14)
PART = style("part", fontName="Body-Bold", fontSize=21, leading=26, textColor=NAVY, spaceBefore=4, spaceAfter=12)
H2 = style("h2", fontName="Body-Bold", fontSize=16, leading=20, textColor=NAVY, spaceBefore=12, spaceAfter=8)
H3 = style("h3", fontName="Body-Bold", fontSize=12.5, leading=16, textColor=BLUE, spaceBefore=8, spaceAfter=6)
ITEM = style("item", leftIndent=22, spaceAfter=4)
NUMBER = style("number", fontName="Body-Bold", fontSize=11.5, textColor=BLUE, spaceAfter=4)
CELL = style("cell", fontSize=8, leading=10.5, spaceAfter=0)
HEAD = style("head", fontName="Body-Bold", fontSize=8, leading=10.5, textColor=colors.white, spaceAfter=0)
CAPTION = style("caption", fontName="Body-Italic", fontSize=8.5, leading=11, textColor=MUTED,
                alignment=TA_CENTER, spaceBefore=5, spaceAfter=10)
QUOTE = style("quote", fontSize=10, leading=14, spaceAfter=0)
CODE = ParagraphStyle("code", fontName="Mono", fontSize=7.6, leading=10.5, textColor=INK)
CODE_COLUMNS = 96
MONO_ADVANCE = 0.602  # Menlo glyph width in em


def inline(text):
    """Markdown inline markup to ReportLab paragraph markup."""
    spans = []

    def keep(match):
        spans.append('<font face="Mono" size="-1">%s</font>' % html.escape(match.group(1), quote=False))
        return "\x00%d\x00" % (len(spans) - 1)

    # Code spans are set aside first so emphasis can wrap them and never reads their contents.
    text = html.escape(re.sub(r"`([^`]+)`", keep, text), quote=False)
    text = re.sub(r"!?\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"(?<![\w*])\*([^*\n]+?)\*(?![\w*])", r"<i>\1</i>", text)
    return re.sub(r"\x00(\d+)\x00", lambda match: spans[int(match.group(1))], text)


def code_block(lines):
    wrapped = []
    for line in lines:
        while len(line) > CODE_COLUMNS:
            wrapped.append(line[:CODE_COLUMNS])
            line = line[CODE_COLUMNS:]
        wrapped.append(line)
    box = Table([[Preformatted("\n".join(wrapped), CODE)]], colWidths=[WIDTH])
    box.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), TINT), ("BOX", (0, 0), (-1, -1), 0.5, RULE),
                             ("LEFTPADDING", (0, 0), (-1, -1), 8), ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                             ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 6)]))
    return [box, Spacer(1, 8)]


def quote_block(text):
    box = Table([[Paragraph(inline(text), QUOTE)]], colWidths=[WIDTH])
    box.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#EAF4FA")),
                             ("LINEBEFORE", (0, 0), (0, -1), 3, BLUE),
                             ("LEFTPADDING", (0, 0), (-1, -1), 12), ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                             ("TOPPADDING", (0, 0), (-1, -1), 8), ("BOTTOMPADDING", (0, 0), (-1, -1), 8)]))
    return [box, Spacer(1, 9)]


def cells_of(row):
    return [cell.strip() for cell in re.split(r"(?<!\\)\|", row.strip().strip("|"))]


def plain_length(cell):
    return max((len(word) for word in re.sub(r"[`*]", "", cell).split()), default=0), len(re.sub(r"[`*]", "", cell))


def table_block(rows):
    header, body = cells_of(rows[0]), [cells_of(row) for row in rows[2:]]
    columns = len(header)
    body = [row + [""] * (columns - len(row)) for row in body]
    # Width follows the content, bounded so one long column cannot starve the others.
    weights, floors = [], []
    for index in range(columns):
        cells = [header[index]] + [row[index] for row in body]
        longest = max(plain_length(cell)[1] for cell in cells)
        widest_word = max(plain_length(cell)[0] for cell in cells)
        weights.append(max(min(longest, 46), 6))
        floors.append(min(widest_word, 24) * 8 * MONO_ADVANCE + 12)  # room for the longest unbreakable word
    scale = min(1, WIDTH * 0.9 / sum(floors))
    floors = [floor * scale for floor in floors]
    spare = WIDTH - sum(floors)
    widths = [floor + spare * weight / sum(weights) for floor, weight in zip(floors, weights)]
    data = [[Paragraph(inline(cell), HEAD) for cell in header]]
    data += [[Paragraph(inline(cell), CELL) for cell in row[:columns]] for row in body]
    table = Table(data, colWidths=widths, repeatRows=1)
    table.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, 0), NAVY), ("GRID", (0, 0), (-1, -1), 0.5, RULE),
                               ("VALIGN", (0, 0), (-1, -1), "TOP"),
                               ("LEFTPADDING", (0, 0), (-1, -1), 5), ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                               ("TOPPADDING", (0, 0), (-1, -1), 3.5), ("BOTTOMPADDING", (0, 0), (-1, -1), 3.5)]))
    return [table, Spacer(1, 10)]


def image_block(base, alt, target, caption=True):
    path = (base / target).resolve()
    width, height = ImageReader(str(path)).getSize()
    flowables = [Image(str(path), width=WIDTH, height=WIDTH * height / width)]
    flowables.append(Paragraph(inline(alt), CAPTION) if caption and alt else Spacer(1, 10))
    return [KeepTogether(flowables)]


def numbered(number, text):
    row = Table([[Paragraph(str(number), NUMBER), Paragraph(inline(text), style("step", spaceAfter=0))]],
                colWidths=[22, WIDTH - 22])
    row.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0),
                             ("RIGHTPADDING", (0, 0), (-1, -1), 0), ("TOPPADDING", (0, 0), (-1, -1), 1),
                             ("BOTTOMPADDING", (0, 0), (-1, -1), 4)]))
    return row


def build(markdown, base):
    lines = markdown.splitlines()
    story, title, index, first_image = [], None, 0, True
    while index < len(lines):
        line = lines[index]
        stripped = line.strip()
        if not stripped or stripped == "---":
            index += 1
        elif stripped.startswith("```"):
            block = []
            index += 1
            while index < len(lines) and not lines[index].strip().startswith("```"):
                block.append(lines[index])
                index += 1
            index += 1
            story += code_block(block)
        elif line.startswith("    ") and not re.match(r"\s*(\d+\.|-)\s", line):
            block = []
            while index < len(lines) and (lines[index].startswith("    ") or not lines[index].strip()):
                block.append(lines[index][4:])
                index += 1
            while block and not block[-1].strip():
                block.pop()
            story += code_block(block)
        elif stripped.startswith("# "):
            text = stripped[2:]
            if title is None:
                title = re.sub(r"^Tutorial:\s*", "", text)
                story += [Paragraph("CRYPTOCARVER / GUIA VISUAL", KICKER), Paragraph(inline(title), TITLE)]
            else:
                story += [PageBreak(), Paragraph(inline(text), PART)]
            index += 1
        elif stripped.startswith("## "):
            story.append(Paragraph(inline(stripped[3:]), H2))
            index += 1
        elif stripped.startswith("### "):
            story.append(Paragraph(inline(stripped[4:]), H3))
            index += 1
        elif stripped.startswith("|") and index + 1 < len(lines) and re.match(r"\s*\|[\s:|-]+\|\s*$", lines[index + 1]):
            rows = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                rows.append(lines[index])
                index += 1
            story += table_block(rows)
        elif stripped.startswith(">"):
            block = []
            while index < len(lines) and lines[index].strip().startswith(">"):
                block.append(lines[index].strip().lstrip(">").strip())
                index += 1
            story += quote_block(" ".join(block))
        elif re.match(r"!\[[^\]]*\]\([^)]+\)$", stripped):
            alt, target = re.match(r"!\[([^\]]*)\]\(([^)]+)\)$", stripped).groups()
            story += image_block(base, alt, target, caption=not first_image)
            first_image = False
            index += 1
        elif re.match(r"\d+\.\s", stripped):
            while index < len(lines) and re.match(r"\d+\.\s", lines[index].strip()):
                number, text = re.match(r"(\d+)\.\s+(.*)", lines[index].strip()).groups()
                index += 1
                while index < len(lines) and lines[index].startswith("   ") and lines[index].strip():
                    text += " " + lines[index].strip()
                    index += 1
                story.append(numbered(number, text))
            story.append(Spacer(1, 5))
        elif stripped.startswith("- "):
            while index < len(lines) and lines[index].strip().startswith("- "):
                text = lines[index].strip()[2:]
                index += 1
                while index < len(lines) and lines[index].startswith("  ") and lines[index].strip() \
                        and not lines[index].strip().startswith("- "):
                    text += " " + lines[index].strip()
                    index += 1
                story.append(Paragraph(inline(text), ITEM, bulletText="•"))
            story.append(Spacer(1, 4))
        else:
            block = []
            while index < len(lines) and lines[index].strip() and not re.match(
                    r"\s*(#{1,3}\s|```|\||>|!\[|- |\d+\.\s)", lines[index]) and lines[index].strip() != "---":
                block.append(lines[index].strip())
                index += 1
            story.append(Paragraph(inline(" ".join(block)), BODY))
    return title, story


def footer(canvas, document):
    canvas.saveState()
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.6)
    canvas.line(MARGIN, 44, A4[0] - MARGIN, 44)
    canvas.setFont("Body", 9)
    canvas.setFillColor(MUTED)
    canvas.drawString(MARGIN, 30, "CryptoCarver - Tutorial visual")
    canvas.drawRightString(A4[0] - MARGIN, 30, "Pagina %d" % document.page)
    canvas.restoreState()


def main():
    source = Path(sys.argv[1]).resolve()
    target = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else source.parent / "pdfs" / (source.stem + ".pdf")
    title, story = build(source.read_text(encoding="utf-8"), source.parent)
    document = SimpleDocTemplate(str(target), pagesize=A4, leftMargin=MARGIN, rightMargin=MARGIN, topMargin=56,
                                 bottomMargin=62, title="Tutorial: " + (title or source.stem), author="CryptoCarver",
                                 subject="Tutorial visual de CryptoCarver")
    document.build(story, onFirstPage=footer, onLaterPages=footer)
    print("%s (%d paginas)" % (target, document.page))


if __name__ == "__main__":
    main()
