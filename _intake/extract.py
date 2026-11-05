import pdfplumber, pypdfium2 as pdfium
p = r"C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics\_intake\so-do-nghiep-vu.pdf"
out = []
with pdfplumber.open(p) as pdf:
    out.append(f"PAGES: {len(pdf.pages)}")
    for i, page in enumerate(pdf.pages):
        out.append(f"\n===== PAGE {i+1} {page.width}x{page.height} =====")
        out.append(page.extract_text() or "")
open(r"C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics\_intake\pdf-text.txt","w",encoding="utf-8").write("\n".join(out))

pdf = pdfium.PdfDocument(p)
for i in range(len(pdf)):
    page = pdf[i]
    img = page.render(scale=2.2).to_pil()
    img.save(rf"C:\Hoc\04_PERSONAL_PROJECTS\AppLogictics\_intake\page-{i+1}.png")
print("OK", len(pdf))
