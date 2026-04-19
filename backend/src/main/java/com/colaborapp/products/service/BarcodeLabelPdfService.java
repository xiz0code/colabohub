package com.colaborapp.products.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.colaborapp.products.web.dto.BarcodeLabelRequest;
import com.colaborapp.products.web.dto.ProductResponse;

@Service
public class BarcodeLabelPdfService {

    private static final String[] CODE128_PATTERNS = {
            "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
            "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
            "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
            "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
            "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
            "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
            "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
            "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
            "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
            "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
            "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    };

    public byte[] generateLabels(List<ProductResponse> products, BarcodeLabelRequest request) {
        List<ProductResponse> labels = new ArrayList<>();
        for (BarcodeLabelRequest.BarcodeLabelItemRequest item : request.items()) {
            ProductResponse product = products.stream()
                    .filter(candidate -> candidate.id().equals(item.productId()))
                    .findFirst()
                    .orElseThrow();
            for (int index = 0; index < item.quantity(); index++) {
                labels.add(product);
            }
        }

        PdfBuilder pdf = new PdfBuilder();
        pdf.beginPage();

        double pageWidth = 595d;
        double pageHeight = 842d;
        double leftMargin = 28.35d;
        double rightMargin = 28.35d;
        double topMargin = 42.5d;
        double bottomMargin = 42.5d;
        double labelWidth = 124.0d;
        double labelHeight = 56.0d;
        int columns = 4;
        double gapY = 8d;
        int rows = (int) Math.floor((pageHeight - topMargin - bottomMargin + gapY) / (labelHeight + gapY));
        double availableWidth = pageWidth - leftMargin - rightMargin - (columns * labelWidth);
        double gapX = availableWidth / Math.max(columns - 1, 1);

        for (int index = 0; index < labels.size(); index++) {
            if (index > 0 && index % (columns * rows) == 0) {
                pdf.endPage(pageWidth, pageHeight);
                pdf.beginPage();
            }

            int pageIndex = index % (columns * rows);
            int column = pageIndex % columns;
            int row = pageIndex / columns;
            double x = leftMargin + (column * (labelWidth + gapX));
            double yTop = pageHeight - topMargin - (row * (labelHeight + gapY));

            drawLabel(pdf, labels.get(index), request.includeCollaboratorName(), x, yTop, labelWidth, labelHeight);
        }

        pdf.endPage(pageWidth, pageHeight);
        return pdf.build();
    }

    private void drawLabel(PdfBuilder pdf, ProductResponse product, boolean includeCollaboratorName, double x, double yTop, double width, double height) {
        double bottom = yTop - height;
        double inset = 6d;
        pdf.roundedRect(x, bottom, width, height, 10d, "1 1 1", "0.82 0.78 0.9");
        pdf.text(x + inset, yTop - 10.5d, 6.6d, truncate(product.name(), 24), true);
        pdf.text(x + inset, yTop - 19d, 5.8d, formatPrice(product.salePrice()), true);
        if (includeCollaboratorName && product.ownerFullName() != null && !product.ownerFullName().isBlank()) {
            pdf.text(x + inset, yTop - 27.5d, 5.1d, truncate(product.ownerFullName(), 20), false);
        }

        drawCode128(pdf, product.barcode(), x + inset, bottom + 12d, width - (inset * 2), 13d);
        pdf.text(x + inset, bottom + 4.4d, 5.0d, product.barcode(), false);
    }

    private void drawCode128(PdfBuilder pdf, String barcode, double x, double y, double maxWidth, double height) {
        List<Integer> codes = new ArrayList<>();
        codes.add(104);
        for (char character : barcode.toCharArray()) {
            if (character < 32 || character > 126) {
                throw new IllegalArgumentException("Unsupported barcode content for CODE128-B.");
            }
            codes.add(character - 32);
        }

        int checksum = 104;
        for (int index = 1; index < codes.size(); index++) {
            checksum += codes.get(index) * index;
        }
        checksum %= 103;
        codes.add(checksum);
        codes.add(106);

        List<Integer> modules = new ArrayList<>();
        for (Integer code : codes) {
            String pattern = CODE128_PATTERNS[code];
            for (char digit : pattern.toCharArray()) {
                modules.add(Character.digit(digit, 10));
            }
        }

        int totalModules = modules.stream().mapToInt(Integer::intValue).sum();
        double moduleWidth = Math.min(0.92d, maxWidth / totalModules);
        double renderedWidth = totalModules * moduleWidth;
        double cursor = x + Math.max((maxWidth - renderedWidth) / 2d, 0d);
        boolean bar = true;
        for (Integer module : modules) {
            double sectionWidth = module * moduleWidth;
            if (bar) {
                pdf.fillRect(cursor, y, sectionWidth, height, "0.2 0.16 0.28");
            }
            cursor += sectionWidth;
            bar = !bar;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "...";
    }

    private String formatPrice(java.math.BigDecimal salePrice) {
        if (salePrice == null) {
            return "$0";
        }
        return "$" + salePrice.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString().replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
    }

    private static final class PdfBuilder {
        private final List<String> objects = new ArrayList<>();
        private final StringBuilder currentPage = new StringBuilder();
        private final List<Integer> pageObjectNumbers = new ArrayList<>();
        private int pagesObjectNumber;

        private void beginPage() {
            currentPage.setLength(0);
        }

        private void roundedRect(double x, double y, double width, double height, double radius, String fillRgb, String strokeRgb) {
            // Soft rectangle fallback; using straight lines keeps the PDF implementation simple and compatible.
            fillRect(x, y, width, height, fillRgb);
            strokeRect(x, y, width, height, strokeRgb, 0.8d);
        }

        private void fillRect(double x, double y, double width, double height, String fillRgb) {
            currentPage.append(fillRgb).append(" rg\n");
            currentPage.append(format(Locale.US, "%.2f %.2f %.2f %.2f re f\n", x, y, width, height));
        }

        private void strokeRect(double x, double y, double width, double height, String strokeRgb, double lineWidth) {
            currentPage.append(strokeRgb).append(" RG\n");
            currentPage.append(format(Locale.US, "%.2f w\n", lineWidth));
            currentPage.append(format(Locale.US, "%.2f %.2f %.2f %.2f re S\n", x, y, width, height));
        }

        private void text(double x, double y, double size, String value, boolean bold) {
            String fontAlias = bold ? "/F2" : "/F1";
            currentPage.append("BT\n");
            currentPage.append(fontAlias).append(' ').append(format(Locale.US, "%.2f", size)).append(" Tf\n");
            currentPage.append("0.26 0.22 0.34 rg\n");
            currentPage.append(format(Locale.US, "%.2f %.2f Td\n", x, y));
            currentPage.append('(').append(escape(value)).append(") Tj\n");
            currentPage.append("ET\n");
        }

        private void endPage(double pageWidth, double pageHeight) {
            int contentObjectNumber = addObject(streamObject(currentPage.toString()));
            int pageObjectNumber = addObject("""
                    <<
                    /Type /Page
                    /Parent %d 0 R
                    /MediaBox [0 0 %.2f %.2f]
                    /Resources <<
                      /Font <<
                        /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
                        /F2 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>
                      >>
                    >>
                    /Contents %d 0 R
                    >>
                    """.formatted(0, pageWidth, pageHeight, contentObjectNumber));
            pageObjectNumbers.add(pageObjectNumber);
        }

        private byte[] build() {
            pagesObjectNumber = objects.size() + 1;

            List<String> rewrittenObjects = new ArrayList<>(objects.size());
            for (String object : objects) {
                rewrittenObjects.add(object.replace("/Parent 0 0 R", "/Parent " + pagesObjectNumber + " 0 R"));
            }
            objects.clear();
            objects.addAll(rewrittenObjects);

            StringBuilder kids = new StringBuilder();
            for (Integer pageObjectNumber : pageObjectNumbers) {
                kids.append(pageObjectNumber).append(" 0 R ");
            }
            addObject("""
                    <<
                    /Type /Pages
                    /Kids [%s]
                    /Count %d
                    >>
                    """.formatted(kids, pageObjectNumbers.size()));

            int catalogObjectNumber = addObject("""
                    <<
                    /Type /Catalog
                    /Pages %d 0 R
                    >>
                    """.formatted(pagesObjectNumber));

            StringBuilder file = new StringBuilder("%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(file.toString().getBytes(StandardCharsets.ISO_8859_1).length);
                file.append(index + 1).append(" 0 obj\n");
                file.append(objects.get(index));
                file.append("\nendobj\n");
            }

            int xrefOffset = file.toString().getBytes(StandardCharsets.ISO_8859_1).length;
            file.append("xref\n0 ").append(objects.size() + 1).append('\n');
            file.append("0000000000 65535 f \n");
            for (int index = 1; index < offsets.size(); index++) {
                file.append(String.format(Locale.US, "%010d 00000 n \n", offsets.get(index)));
            }
            file.append("trailer\n");
            file.append("<< /Size ").append(objects.size() + 1).append(" /Root ").append(catalogObjectNumber).append(" 0 R >>\n");
            file.append("startxref\n").append(xrefOffset).append('\n');
            file.append("%%EOF");
            return file.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        private int addObject(String body) {
            objects.add(body.trim());
            return objects.size();
        }

        private String streamObject(String content) {
            byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);
            return """
                    <<
                    /Length %d
                    >>
                    stream
                    %s
                    endstream
                    """.formatted(bytes.length, content);
        }

        private String escape(String value) {
            return value
                    .replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)");
        }

        private String format(Locale locale, String template, Object... args) {
            return String.format(locale, template, args);
        }
    }
}
