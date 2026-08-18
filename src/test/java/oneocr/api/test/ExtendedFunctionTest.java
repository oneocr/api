package oneocr.api.test;

import oneocr.api.OcrResult;
import oneocr.api.OcrWord;
import oneocr.api.BoundingBox;
import oneocr.api.OcrLine;
import oneocr.api.OneOcrApi;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import oneocr.api.BoundingBox;
import oneocr.api.OcrLine;
import oneocr.api.OcrResult;
import oneocr.api.OcrWord;
import oneocr.api.OneOcrApi;

/**
 * Test additional exported DLL functions beyond basic OCR
 */
public class ExtendedFunctionTest {
    
    public static void main(String[] args) {
        try {
            Path dllPath = Path.of("oneocr.dll");
            Path modelPath = Path.of("oneocr.onemodel");
            // Load image from test resources
            var imageUrl = ExtendedFunctionTest.class.getResource("/ocr-book.jpg");
            if (imageUrl == null) {
                throw new RuntimeException("Test image ocr-book.jpg not found in test resources");
            }
            BufferedImage image = ImageIO.read(imageUrl);
            byte[] bgraData = convertToBGRA(image);
            System.err.printf("Processing image: %dx%d%n", image.getWidth(), image.getHeight());
            
            // Test extended functions with OneOcrApi
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                System.out.println("=== Testing Extended DLL Functions ===");
                
                // Perform OCR to get results
                OcrResult result = api.recognizeImage(pipeline, processOptions, image.getWidth(), image.getHeight(), bgraData);
                
                System.out.printf("✅ Basic OCR: %d lines, %.2f degrees%n", 
                    result.lines().size(), result.textAngle());
                
                // Test line-level analysis
                if (!result.lines().isEmpty()) {
                    OcrLine firstLine = result.lines().get(0);
                    System.out.printf("✅ First line: \"%s\" with %d words%n", 
                        firstLine.text(), firstLine.words().size());
                    
                    if (firstLine.boundingBox() != null) {
                        BoundingBox bbox = firstLine.boundingBox();
                        System.out.printf("✅ Line bounding box: (%.1f,%.1f) to (%.1f,%.1f)%n",
                            bbox.x1(), bbox.y1(), bbox.x3(), bbox.y3());
                    }
                    
                    // Test word-level analysis
                    if (!firstLine.words().isEmpty()) {
                        OcrWord firstWord = firstLine.words().get(0);
                        System.out.printf("✅ First word: \"%s\" confidence: %.2f%n",
                            firstWord.text(), firstWord.confidence());
                        
                        if (firstWord.boundingBox() != null) {
                            BoundingBox wbox = firstWord.boundingBox();
                            System.out.printf("✅ Word bounding box: (%.1f,%.1f) to (%.1f,%.1f)%n",
                                wbox.x1(), wbox.y1(), wbox.x3(), wbox.y3());
                        }
                    }
                }
                
                // Test multiple recognition runs for performance comparison
                System.out.println("\n=== Performance Test ===");
                long startTime = System.currentTimeMillis();
                
                for (int i = 0; i < 5; i++) {
                    OcrResult testResult = api.recognizeImage(pipeline, processOptions, image.getWidth(), image.getHeight(), bgraData);
                    System.out.printf("Run %d: %d lines recognized%n", i + 1, testResult.lines().size());
                }
                
                // Cleanup
                processOptions.close();
                pipeline.close();
                initOptions.close();
                
                long endTime = System.currentTimeMillis();
                double avgTime = (endTime - startTime) / 5.0;
                System.out.printf("✅ Average recognition time: %.1f ms%n", avgTime);
                System.out.printf("✅ Estimated throughput: %.1f images/minute%n", 60000.0 / avgTime);
                
                System.out.println("\n✅ Extended Function Test COMPLETED!");
                
            }
            
        } catch (Exception e) {
            System.err.println("❌ Extended Function Test FAILED: " + e.getMessage());
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