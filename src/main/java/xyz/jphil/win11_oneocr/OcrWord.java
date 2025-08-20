package xyz.jphil.win11_oneocr;

/**
 * Represents a recognized word with text, bounding box and confidence
 */
public record OcrWord(
    String text,
    BoundingBox boundingBox,
    double confidence
) {
}