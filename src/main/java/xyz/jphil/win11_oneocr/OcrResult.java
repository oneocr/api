package xyz.jphil.win11_oneocr;

import java.util.List;

/**
 * Complete OCR result with text, angle and hierarchical structure
 */
public record OcrResult(
    String text,
    double textAngle,
    List<OcrLine> lines
) {
}