package xyz.jphil.win11_oneocr.test;

import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.OcrResult;
import xyz.jphil.win11_oneocr.OneOcrApi;

/**
 * Quick Memory Leak Test - focuses on identifying the primary leak source
 */
public class QuickMemoryLeakTest {
    
    public static void main(String[] args) {
        System.out.println("=== Quick Memory Leak Detection ===");
        
        try {
            // Load test image once
            var imageUrl = QuickMemoryLeakTest.class.getResource("/ocr-book.jpg");
            if (imageUrl == null) {
                throw new RuntimeException("Test image ocr-book.jpg not found in test resources");
            }
            BufferedImage testImage = ImageIO.read(imageUrl);
            byte[] bgraData = convertToBGRA(testImage);
            
            System.out.printf("Test image loaded: %dx%d%n", testImage.getWidth(), testImage.getHeight());
            
            // Test the current problematic pattern (resource recreation)
            testResourceRecreationLeak(testImage, bgraData);
            
            // Test the recommended pattern (resource reuse) 
            testResourceReuseLeak(testImage, bgraData);
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== Quick Test Complete ===");
    }
    
    /**
     * Test current pattern: recreate resources each time (like ThreadSafetyTest does)
     */
    private static void testResourceRecreationLeak(BufferedImage image, byte[] bgraData) {
        System.out.println("\nTEST: Resource Recreation Pattern (Current ThreadSafetyTest approach)");
        
        final int ITERATIONS = 20; // Smaller number for quick test
        long startMemory = getUsedMemory();
        System.out.printf("  Start memory: %d KB%n", startMemory/1024);
        
        for (int i = 0; i < ITERATIONS; i++) {
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                OcrResult result = api.recognizeImage(pipeline, processOptions,
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Explicit cleanup in correct order
                processOptions.close();
                pipeline.close();
                initOptions.close();
                
                // Memory sampling
                if (i % 5 == 0 || i == ITERATIONS - 1) {
                    System.gc(); // Force GC
                    long currentMemory = getUsedMemory();
                    System.out.printf("    Iteration %d: %d KB%n", i, currentMemory/1024);
                }
                
            } catch (Exception e) {
                System.err.printf("    Iteration %d failed: %s%n", i, e.getMessage());
            }
        }
        
        System.gc();
        System.gc(); // Double GC
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        long endMemory = getUsedMemory();
        long memoryIncrease = endMemory - startMemory;
        
        System.out.printf("  Final memory: %d KB (increase: %+d KB)%n", 
            endMemory/1024, memoryIncrease/1024);
        
        if (memoryIncrease > 20 * 1024 * 1024) { // > 20MB
            System.out.printf("  ⚠️  LEAK DETECTED: %d KB increase suggests memory leak%n", 
                memoryIncrease/1024);
        }
    }
    
    /**
     * Test recommended pattern: reuse resources
     */
    private static void testResourceReuseLeak(BufferedImage image, byte[] bgraData) {
        System.out.println("\nTEST: Resource Reuse Pattern (Recommended approach)");
        
        final int ITERATIONS = 20; // Same number for comparison
        long startMemory = getUsedMemory();
        System.out.printf("  Start memory: %d KB%n", startMemory/1024);
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            for (int i = 0; i < ITERATIONS; i++) {
                try {
                    OcrResult result = api.recognizeImage(pipeline, processOptions,
                        image.getWidth(), image.getHeight(), bgraData);
                    
                    // Memory sampling 
                    if (i % 5 == 0 || i == ITERATIONS - 1) {
                        System.gc(); // Force GC
                        long currentMemory = getUsedMemory();
                        System.out.printf("    Iteration %d: %d KB%n", i, currentMemory/1024);
                    }
                    
                } catch (Exception e) {
                    System.err.printf("    Iteration %d failed: %s%n", i, e.getMessage());
                }
            }
            
            // Cleanup once at the end
            processOptions.close();
            pipeline.close();
            initOptions.close();
            
        } catch (Exception e) {
            System.err.println("  Resource reuse test failed: " + e.getMessage());
        }
        
        System.gc();
        System.gc(); // Double GC
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        long endMemory = getUsedMemory();
        long memoryIncrease = endMemory - startMemory;
        
        System.out.printf("  Final memory: %d KB (increase: %+d KB)%n", 
            endMemory/1024, memoryIncrease/1024);
        
        if (memoryIncrease > 20 * 1024 * 1024) { // > 20MB  
            System.out.printf("  ⚠️  LEAK DETECTED: %d KB increase suggests memory leak%n", 
                memoryIncrease/1024);
        }
    }
    
    private static long getUsedMemory() {
        var memoryBean = ManagementFactory.getMemoryMXBean();
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    private static byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];
        
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                bgraData[index++] = (byte) (rgb & 0xFF);        // Blue
                bgraData[index++] = (byte) ((rgb >> 8) & 0xFF);  // Green  
                bgraData[index++] = (byte) ((rgb >> 16) & 0xFF); // Red
                bgraData[index++] = (byte) ((rgb >> 24) & 0xFF); // Alpha
            }
        }
        
        return bgraData;
    }
}