package xyz.jphil.win11_oneocr;

import java.util.List;

/**
 * Represents a line of text with words and bounding box
 */
public record OcrLine(
    String text,
    BoundingBox boundingBox,
    List<OcrWord> words
) {
}