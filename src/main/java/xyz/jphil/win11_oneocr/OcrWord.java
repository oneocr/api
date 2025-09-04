package xyz.jphil.win11_oneocr;

/**
 * Represents a recognized word with text, bounding box and confidence
 */
public final class OcrWord {
    String text;
    String llmCorrection;
    BoundingBox boundingBox;
    double confidence;
    
    public static OcrWord ocrWord(String text, BoundingBox boundingBox, double confidence){
        return new OcrWord().text(text).boundingBox(boundingBox).confidence(confidence);
    }
    
    public static OcrWord ocrWord(String text, BoundingBox boundingBox, double confidence, String llmCorrection){
        return new OcrWord().text(text).boundingBox(boundingBox).confidence(confidence).llmCorrection(llmCorrection);
    }

    public String text() {
        return text;
    }

    public OcrWord text(String text) {
        this.text = text;
        return this;
    }

    public String llmCorrection() {
        return llmCorrection;
    }

    public OcrWord llmCorrection(String llmCorrection) {
        if(llmCorrection==null)llmCorrection = "";
        this.llmCorrection = llmCorrection;
        return this;
    }

    public BoundingBox boundingBox() {
        return boundingBox;
    }

    public OcrWord boundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        return this;
    }

    public double confidence() {
        return confidence;
    }

    public OcrWord confidence(double confidence) {
        this.confidence = confidence;
        return this;
    }
    
    
}