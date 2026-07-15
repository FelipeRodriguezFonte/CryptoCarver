from __future__ import annotations

import html
import re
import sys
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.platypus.tableofcontents import TableOfContents


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "CRYPTOCARVER_ROADMAP_EVOLUCION.md"
OUTPUT = ROOT / "output" / "pdf" / "CryptoCarver_Roadmap_Evolucion.pdf"

NAVY = colors.HexColor("#243B53")
BLUE = colors.HexColor("#0077A8")
CYAN = colors.HexColor("#1BA7C9")
LIGHT_BLUE = colors.HexColor("#EAF6FA")
PALE = colors.HexColor("#F4F7FA")
MID = colors.HexColor("#8FA6B8")
TEXT = colors.HexColor("#24313A")
MUTED = colors.HexColor("#5F6F7A")
GREEN = colors.HexColor("#2D8C6F")


def register_fonts() -> None:
    fonts = {
        "Roadmap": "/System/Library/Fonts/Supplemental/Arial.ttf",
        "Roadmap-Bold": "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "Roadmap-Italic": "/System/Library/Fonts/Supplemental/Arial Italic.ttf",
        "Roadmap-BoldItalic": "/System/Library/Fonts/Supplemental/Arial Bold Italic.ttf",
    }
    for name, path in fonts.items():
        pdfmetrics.registerFont(TTFont(name, path))
    pdfmetrics.registerFontFamily(
        "Roadmap", normal="Roadmap", bold="Roadmap-Bold", italic="Roadmap-Italic", boldItalic="Roadmap-BoldItalic"
    )


class RoadmapDocTemplate(BaseDocTemplate):
    def __init__(self, filename: str):
        super().__init__(
            filename,
            pagesize=A4,
            rightMargin=17 * mm,
            leftMargin=17 * mm,
            topMargin=20 * mm,
            bottomMargin=17 * mm,
            title="CryptoCarver - Plan de evolución",
            author="CryptoCarver",
            subject="Roadmap funcional, técnico y de interfaz",
        )
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id="body")
        self.addPageTemplates(PageTemplate(id="roadmap", frames=[frame], onPage=self.draw_page))
        self._bookmark_id = 0

    def draw_page(self, canvas, doc) -> None:
        canvas.saveState()
        width, height = A4
        if doc.page == 1:
            canvas.setFillColor(NAVY)
            canvas.rect(0, 0, width, height, fill=1, stroke=0)
            canvas.setFillColor(CYAN)
            canvas.rect(0, height - 8 * mm, width, 8 * mm, fill=1, stroke=0)
        else:
            canvas.setStrokeColor(colors.HexColor("#D6E1E8"))
            canvas.line(self.leftMargin, height - 13 * mm, width - self.rightMargin, height - 13 * mm)
            canvas.setFont("Roadmap", 7.5)
            canvas.setFillColor(MUTED)
            canvas.drawString(self.leftMargin, height - 10 * mm, "CRYPTOCARVER / ROADMAP DE EVOLUCIÓN")
            canvas.drawRightString(width - self.rightMargin, 9 * mm, f"Página {doc.page}")
            canvas.setFillColor(BLUE)
            canvas.rect(self.leftMargin, 12 * mm, 26 * mm, 1.2 * mm, fill=1, stroke=0)
        canvas.restoreState()

    def afterFlowable(self, flowable) -> None:
        if isinstance(flowable, Paragraph):
            level = getattr(flowable, "toc_level", None)
            if level is not None:
                text = flowable.getPlainText()
                self._bookmark_id += 1
                key = f"heading-{self._bookmark_id}"
                self.canv.bookmarkPage(key)
                self.canv.addOutlineEntry(text, key, level=level, closed=level > 0)
                self.notify("TOCEntry", (level, text, self.page, key))


def make_styles():
    sample = getSampleStyleSheet()
    styles = {}
    styles["Title"] = ParagraphStyle(
        "Title", parent=sample["Title"], fontName="Roadmap-Bold", fontSize=30, leading=34,
        textColor=colors.white, alignment=TA_LEFT, spaceAfter=8 * mm,
    )
    styles["Subtitle"] = ParagraphStyle(
        "Subtitle", parent=sample["Normal"], fontName="Roadmap", fontSize=14, leading=20,
        textColor=colors.HexColor("#D8EEF5"), spaceAfter=12 * mm,
    )
    styles["CoverMeta"] = ParagraphStyle(
        "CoverMeta", parent=sample["Normal"], fontName="Roadmap", fontSize=9.5, leading=15,
        textColor=colors.white, leftIndent=4 * mm,
    )
    styles["H1"] = ParagraphStyle(
        "H1", parent=sample["Heading1"], fontName="Roadmap-Bold", fontSize=18, leading=22,
        textColor=NAVY, spaceBefore=5 * mm, spaceAfter=3 * mm, keepWithNext=True,
    )
    styles["H2"] = ParagraphStyle(
        "H2", parent=sample["Heading2"], fontName="Roadmap-Bold", fontSize=13, leading=16,
        textColor=BLUE, spaceBefore=4 * mm, spaceAfter=2 * mm, keepWithNext=True,
    )
    styles["H3"] = ParagraphStyle(
        "H3", parent=sample["Heading3"], fontName="Roadmap-Bold", fontSize=10.5, leading=13,
        textColor=GREEN, spaceBefore=3 * mm, spaceAfter=1.5 * mm, keepWithNext=True,
    )
    styles["Body"] = ParagraphStyle(
        "Body", parent=sample["BodyText"], fontName="Roadmap", fontSize=8.6, leading=12.2,
        textColor=TEXT, spaceAfter=1.8 * mm, alignment=TA_LEFT,
    )
    styles["Bullet"] = ParagraphStyle(
        "Bullet", parent=styles["Body"], leftIndent=5 * mm, firstLineIndent=-3.2 * mm,
        bulletIndent=1.2 * mm, spaceAfter=1.1 * mm,
    )
    styles["Code"] = ParagraphStyle(
        "Code", parent=styles["Body"], fontName="Courier", fontSize=7.6, leading=10,
        leftIndent=5 * mm, rightIndent=5 * mm, borderColor=colors.HexColor("#CBD8E0"),
        borderWidth=0.5, borderPadding=6, backColor=PALE, spaceBefore=2 * mm, spaceAfter=3 * mm,
    )
    styles["TOCHeading"] = ParagraphStyle(
        "TOCHeading", parent=styles["H1"], fontSize=21, spaceAfter=5 * mm,
    )
    styles["TOC1"] = ParagraphStyle(
        "TOC1", fontName="Roadmap", fontSize=9, leading=13, leftIndent=0, firstLineIndent=0,
        textColor=TEXT, spaceBefore=1 * mm,
    )
    styles["TOC2"] = ParagraphStyle(
        "TOC2", fontName="Roadmap", fontSize=8, leading=11, leftIndent=7 * mm, firstLineIndent=0,
        textColor=MUTED,
    )
    return styles


def inline(text: str) -> str:
    value = html.escape(text, quote=False)
    value = re.sub(r"`([^`]+)`", r'<font name="Courier">\1</font>', value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", value)
    value = re.sub(r"\*([^*]+)\*", r"<i>\1</i>", value)
    value = re.sub(
        r"(https?://[^\s<]+)",
        lambda m: f'<link href="{m.group(1)}" color="#0077A8">{m.group(1)}</link>',
        value,
    )
    return value


def table_flowable(rows: list[list[str]], styles, available_width: float):
    if not rows:
        return Spacer(1, 1)
    columns = len(rows[0])
    lengths = [max(5, max(len(row[i]) if i < len(row) else 0 for row in rows)) for i in range(columns)]
    weights = [min(length, 34) for length in lengths]
    total = sum(weights)
    widths = [available_width * weight / total for weight in weights]
    data = []
    for row_index, row in enumerate(rows):
        style = ParagraphStyle(
            f"Table-{row_index}", parent=styles["Body"], fontName="Roadmap-Bold" if row_index == 0 else "Roadmap",
            fontSize=7.2 if columns <= 4 else 6.6, leading=9.2 if columns <= 4 else 8.3,
            textColor=colors.white if row_index == 0 else TEXT, spaceAfter=0,
        )
        data.append([Paragraph(inline(row[i] if i < len(row) else ""), style) for i in range(columns)])
    table = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT", splitByRow=1)
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#C8D5DE")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PALE]),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return table


def parse_markdown(lines: list[str], styles, available_width: float):
    story = []
    index = 0
    code = None
    while index < len(lines):
        raw = lines[index].rstrip("\n")
        stripped = raw.strip()
        if code is not None:
            if stripped.startswith("```"):
                story.append(Paragraph(html.escape("\n".join(code)).replace("\n", "<br/>"), styles["Code"]))
                code = None
            else:
                code.append(raw)
            index += 1
            continue
        if stripped.startswith("```"):
            code = []
            index += 1
            continue
        if stripped.startswith("|"):
            table_lines = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                table_lines.append(lines[index].strip())
                index += 1
            rows = []
            for line_index, line in enumerate(table_lines):
                cells = [cell.strip() for cell in line.strip("|").split("|")]
                if line_index == 1 and all(re.fullmatch(r":?-+:?", cell) for cell in cells):
                    continue
                rows.append(cells)
            story.extend([table_flowable(rows, styles, available_width), Spacer(1, 3 * mm)])
            continue
        if stripped.startswith("## "):
            paragraph = Paragraph(inline(stripped[3:]), styles["H1"])
            paragraph.toc_level = 0
            story.append(paragraph)
        elif stripped.startswith("### "):
            paragraph = Paragraph(inline(stripped[4:]), styles["H2"])
            paragraph.toc_level = 1
            story.append(paragraph)
        elif stripped.startswith("#### "):
            story.append(Paragraph(inline(stripped[5:]), styles["H3"]))
        elif stripped in {"---", "***"}:
            story.append(Spacer(1, 2 * mm))
        elif re.match(r"^-\s+", stripped):
            story.append(Paragraph(inline(stripped[2:]), styles["Bullet"], bulletText="•"))
        elif re.match(r"^\d+\.\s+", stripped):
            match = re.match(r"^(\d+)\.\s+(.*)", stripped)
            story.append(Paragraph(f'<b>{match.group(1)}.</b> {inline(match.group(2))}', styles["Bullet"]))
        elif stripped:
            story.append(Paragraph(inline(stripped), styles["Body"]))
        index += 1
    return story


def build() -> None:
    register_fonts()
    styles = make_styles()
    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    doc = RoadmapDocTemplate(str(OUTPUT))
    story = []

    story.append(Spacer(1, 46 * mm))
    story.append(Paragraph("CryptoCarver", styles["Title"]))
    story.append(Paragraph("Plan de evolución funcional, técnica y de interfaz", styles["Subtitle"]))
    meta = [
        "Versión del plan: 1.0",
        "Fecha: 12 de julio de 2026",
        "Horizonte: 12 meses",
        "Documento vivo para priorización y seguimiento",
    ]
    meta_table = Table([[Paragraph(line, styles["CoverMeta"])] for line in meta], colWidths=[125 * mm])
    meta_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#304F67")),
        ("BOX", (0, 0), (-1, -1), 0.8, CYAN),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 28 * mm))
    story.append(Paragraph(
        "Una hoja de ruta para evolucionar CryptoCarver como laboratorio criptográfico modular, explicable, reproducible y verificable.",
        ParagraphStyle("CoverNote", parent=styles["Subtitle"], fontSize=11, leading=16, textColor=colors.white),
    ))
    story.append(PageBreak())

    story.append(Paragraph("Contenido", styles["TOCHeading"]))
    section_titles = [line[3:] for line in lines if line.startswith("## ")]
    toc_cells = [Paragraph(inline(title), styles["TOC1"]) for title in section_titles]
    if len(toc_cells) % 2:
        toc_cells.append(Paragraph("", styles["TOC1"]))
    toc_rows = [[toc_cells[i], toc_cells[i + 1]] for i in range(0, len(toc_cells), 2)]
    toc_table = Table(toc_rows, colWidths=[doc.width / 2, doc.width / 2], hAlign="LEFT")
    toc_table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LINEBELOW", (0, 0), (-1, -1), 0.25, colors.HexColor("#DCE6EC")),
    ]))
    story.append(toc_table)
    story.append(PageBreak())

    start = next(i for i, line in enumerate(lines) if line.startswith("## 1."))
    story.extend(parse_markdown(lines[start:], styles, doc.width))
    doc.build(story)


if __name__ == "__main__":
    try:
        build()
        print(OUTPUT)
    except Exception as exc:
        print(f"PDF generation failed: {exc}", file=sys.stderr)
        raise
