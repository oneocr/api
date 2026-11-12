package oneocr.api;

import java.util.List;

/**
 * Represents a line of text with words and bounding box.
 *
 * <p>{@code boundingBox} has always been a full quadrilateral rather than an axis-aligned box, so
 * a rotated or skewed line keeps its real shape; {@link BoundingBox#getAxisAlignedBounds()} flattens
 * it when a rectangle is what is wanted.
 *
 * <p>{@code script} and {@code vertical} exist because the ONNX backend knows both and the DLL does
 * not: the ONNX pipeline picks a per-script recognizer for every line and detects vertical writing.
 * Both are nullable and mean "not reported by whichever engine ran" when null — the DLL path leaves
 * them null, and nothing may infer from that that a line is Latin or horizontal.
 */
public record OcrLine(
    String text,
    BoundingBox boundingBox,
    List<OcrWord> words,
    String script,
    Boolean vertical
) {
    /** A line from an engine that reports neither script nor writing direction. */
    public OcrLine(String text, BoundingBox boundingBox, List<OcrWord> words) {
        this(text, boundingBox, words, null, null);
    }
}
