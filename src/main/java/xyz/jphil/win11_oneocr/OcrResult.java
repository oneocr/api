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
    public OcrLine lineAtIndex(int index){
        if(index<0)return null;
        if(lines==null)return null;
        return lines.get(index);
    }
    
    public OcrWord wordAtIndex(int index){
        int totalIdx = 0;
        if(index<0)return null;
        if(lines==null)return null;
        for (int i = 0; i < lines.size() && totalIdx <= index; i++) {
            var line = lines.get(i);
            if(line==null)continue;
            var words = line.words();
            if(words==null)continue;
            if(totalIdx+words.size() < index){
                totalIdx+=words.size();
            }else {
                return words.get(index-totalIdx);
            }
        }
        return null;
    }
    
    public int linesCount(){
        if(lines==null)return 0;
        return lines.size();
    }
    
    public int wordsCount(){
        if(lines==null)return 0;
        int wordCnt = 0;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if(line==null)continue;
            var words = line.words();
            if(words==null)continue;
            wordCnt += words.size();
        }
        return wordCnt;
    }
    
}