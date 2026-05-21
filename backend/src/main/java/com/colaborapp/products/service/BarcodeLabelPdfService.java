package com.colaborapp.products.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.colaborapp.products.web.dto.BarcodeLabelRequest;
import com.colaborapp.products.web.dto.BarcodeLabelRequest.BarcodeLabelFormat;
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
        Map<Long, ProductResponse> productById = new HashMap<>();
        for (ProductResponse product : products) {
            productById.put(product.id(), product);
        }

        int totalLabels = request.items().stream()
                .mapToInt(BarcodeLabelRequest.BarcodeLabelItemRequest::quantity)
                .sum();
        List<ProductResponse> labels = new ArrayList<>(Math.max(totalLabels, 0));
        for (BarcodeLabelRequest.BarcodeLabelItemRequest item : request.items()) {
            ProductResponse product = productById.get(item.productId());
            if (product == null) {
                throw new IllegalArgumentException("Product not found for barcode label generation.");
            }
            for (int index = 0; index < item.quantity(); index++) {
                labels.add(product);
            }
        }

        return switch (request.format()) {
            case LETTER -> generateSheetLabels(labels, request.includeCollaboratorName(), 612d, 792d);
            case LABEL_30X20 -> generateCompactRollLabels(labels);
            case A4 -> generateSheetLabels(labels, request.includeCollaboratorName(), 595d, 842d);
        };
    }

    private byte[] generateSheetLabels(List<ProductResponse> labels, boolean includeCollaboratorName, double pageWidth, double pageHeight) {
        PdfBuilder pdf = new PdfBuilder();
        pdf.beginPage();

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

            drawSheetLabel(pdf, labels.get(index), includeCollaboratorName, x, yTop, labelWidth, labelHeight);
        }

        pdf.endPage(pageWidth, pageHeight);
        return pdf.build();
    }

    private byte[] generateCompactRollLabels(List<ProductResponse> labels) {
        PdfBuilder pdf = new PdfBuilder();
        double pageWidth = mmToPoints(30d);
        double pageHeight = mmToPoints(20d);

        for (ProductResponse product : labels) {
            pdf.beginPage();
            drawCompactLabel(pdf, product, 0d, pageHeight, pageWidth, pageHeight);
            pdf.endPage(pageWidth, pageHeight);
        }
        return pdf.build();
    }

    private void drawSheetLabel(PdfBuilder pdf, ProductResponse product, boolean includeCollaboratorName, double x, double yTop, double width, double height) {
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

    private void drawCompactLabel(PdfBuilder pdf, ProductResponse product, double x, double yTop, double width, double height) {
        double bottom = yTop - height;
        String collaboratorName = product.ownerFullName() != null && !product.ownerFullName().isBlank()
                ? product.ownerFullName()
                : product.storeName();
        double barcodeWidth = width - 18d;
        double barcodeHeight = 16.3d;
        double barcodeTextSize = 6.2d;
        double barcodeTextY = bottom + 2.8d;
        double barcodeY = bottom + 9.6d;
        double centerX = x + (width / 2d);
        pdf.roundedRect(x + 1d, bottom + 1d, width - 2d, height - 2d, 4d, "1 1 1", "0.82 0.78 0.9");
        pdf.textCentered(centerX, bottom + 40.5d, 6.2d, formatPrice(product.salePrice()), true);
        if (collaboratorName != null && !collaboratorName.isBlank()) {
            pdf.textCentered(centerX, bottom + 34.8d, 3.3d, truncate(collaboratorName, 20), false);
        }
        pdf.textCentered(centerX, bottom + 29.4d, 5.6d, truncate(product.name(), 18), true);
        drawCode128(pdf, product.barcode(), x + ((width - barcodeWidth) / 2d), barcodeY, barcodeWidth, barcodeHeight);
        pdf.textCentered(centerX, barcodeTextY, barcodeTextSize, truncate(product.barcode(), 16), true);
    }

    private void drawCode128(PdfBuilder pdf, String barcode, double x, double y, double maxWidth, double height) {
        List<Integer> codes = encodeCode128(barcode);

        int checksum = codes.get(0);
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
        double moduleWidth = Math.min(1.25d, maxWidth / totalModules);
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

    private List<Integer> encodeCode128(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("Barcode content is required.");
        }

        if (barcode.chars().allMatch(Character::isDigit)) {
            return encodeNumericCode128(barcode);
        }

        List<Integer> codes = new ArrayList<>();
        codes.add(104);
        for (char character : barcode.toCharArray()) {
            if (character < 32 || character > 126) {
                throw new IllegalArgumentException("Unsupported barcode content for CODE128-B.");
            }
            codes.add(character - 32);
        }
        return codes;
    }

    private List<Integer> encodeNumericCode128(String barcode) {
        List<Integer> codes = new ArrayList<>();
        int index = 0;

        if (barcode.length() % 2 != 0) {
            codes.add(104);
            char firstDigit = barcode.charAt(0);
            if (firstDigit < 32 || firstDigit > 126) {
                throw new IllegalArgumentException("Unsupported barcode content for CODE128-B.");
            }
            codes.add(firstDigit - 32);
            codes.add(99);
            index = 1;
        } else {
            codes.add(105);
        }

        for (; index < barcode.length(); index += 2) {
            int pairValue = Integer.parseInt(barcode.substring(index, index + 2));
            codes.add(pairValue);
        }
        return codes;
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

    private double mmToPoints(double millimeters) {
        return millimeters * 72d / 25.4d;
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

        private void textCentered(double centerX, double y, double size, String value, boolean bold) {
            double approximateWidth = value.length() * size * (bold ? 0.31d : 0.27d);
            text(centerX - approximateWidth, y, size, value, bold);
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
