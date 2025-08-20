package xyz.jphil.win11_oneocr.test;

import xyz.jphil.win11_oneocr.OcrResult;
import xyz.jphil.win11_oneocr.OcrLine;
import xyz.jphil.win11_oneocr.OneOcrApi;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.OcrLine;
import xyz.jphil.win11_oneocr.OcrResult;
import xyz.jphil.win11_oneocr.OneOcrApi;

/**
 * Simple test using the cross-JDK compatible OneOcrApi
 */
public class SimpleOcrTest {
    
    public static void main(String[] args) {
        try {
            Path dllPath = Path.of("oneocr.dll");
            Path modelPath = Path.of("oneocr.onemodel");
            // Load image from test resources
            var imageUrl = SimpleOcrTest.class.getResource("/ocr-book.jpg");
            if (imageUrl == null) {
                throw new RuntimeException("Test image ocr-book.jpg not found in test resources");
            }
            BufferedImage image = ImageIO.read(imageUrl);
            byte[] bgraData = convertToBGRA(image);
            System.err.printf("Processing image: %dx%d%n", image.getWidth(), image.getHeight());
            
            // Perform OCR using cross-JDK compatible API with real image processing
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                System.out.println("✅ OneOcrApi initialization successful!");
                
                // Perform actual OCR on image data
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Cleanup
                processOptions.close();
                pipeline.close();
                initOptions.close();
                
                System.out.println("=== OCR Results ===");
                System.out.printf("Text angle: %.2f degrees%n", result.textAngle());
                System.out.printf("Lines found: %d%n", result.lines().size());
                System.out.printf("Total words: %d%n", result.lines().stream().mapToInt(l -> l.words().size()).sum());
                System.out.println();
                
                for (int i = 0; i < result.lines().size() && i < 3; i++) { // Show first 3 lines
                    OcrLine line = result.lines().get(i);
                    System.out.printf("Line %d: \"%s\"%n", i + 1, line.text());
                }
                
                System.out.println("\n✅ OCR Test PASSED - Cross-JDK OneOcrApi works!");
                
            }
            
        } catch (Exception e) {
            System.err.println("❌ OCR Test FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int offset = (y * width + x) * 4;
                
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                bgraData[offset] = (byte) blue;
                bgraData[offset + 1] = (byte) green;
                bgraData[offset + 2] = (byte) red;
                bgraData[offset + 3] = (byte) alpha;
            }
        }
        
        return bgraData;
    }
}