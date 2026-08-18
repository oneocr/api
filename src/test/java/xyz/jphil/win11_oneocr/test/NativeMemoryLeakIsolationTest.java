package xyz.jphil.win11_oneocr.test;

import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.*;

/**
 * Native Memory Leak Isolation Test
 * 
 * Tests different FFM resource allocation patterns to isolate the native memory leak.
 * Run with: -XX:NativeMemoryTracking=detail -Xmx1g
 * Monitor with: jcmd <pid> VM.native_memory summary.diff scale=MB
 */
public class NativeMemoryLeakIsolationTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Native Memory Leak Isolation Test ===");
        System.out.println("Process PID: " + ProcessHandle.current().pid());
        System.out.println("JVM Max Heap: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println("\nRun: jcmd " + ProcessHandle.current().pid() + " VM.native_memory baseline");
        System.out.println("Then: jcmd " + ProcessHandle.current().pid() + " VM.native_memory summary.diff scale=MB");
        
        // Load test image once
        BufferedImage testImage = ImageIO.read(
            NativeMemoryLeakIsolationTest.class.getResourceAsStream("/ocr-book.jpg"));
        System.out.println("Test image loaded: " + testImage.getWidth() + "x" + testImage.getHeight());
        
        System.out.println("\n=== BASELINE - Set jcmd baseline now ===");
        Thread.sleep(5000);
        
        // Test 1: OneOcrApi creation/destruction (no OCR operations)
        System.out.println("\n=== TEST 1: OneOcrApi Creation Only (No OCR) ===");
        testOneOcrApiOnly(25);
        System.gc(); 
        Thread.sleep(2000);
        
        // Test 2: Full OCR operations (current pattern)
        System.out.println("\n=== TEST 2: Full OCR Operations (Current Pattern) ===");
        testFullOcrOperations(testImage, 25);
        System.gc();
        Thread.sleep(2000);
        
        // Test 3: Shared resource pattern (Python approach)
        System.out.println("\n=== TEST 3: Shared Resource Pattern ===");
        testSharedResourcePattern(testImage, 25);
        System.gc();
        Thread.sleep(2000);
        
        System.out.println("\n=== ANALYSIS COMPLETE ===");
        System.out.println("Check native memory diff after each test phase");
        System.out.println("The test with largest native memory growth is the leak source");
    }
    
    /**
     * Test 2: Full OneOcrApi lifecycle without OCR operations
     * This isolates heavy resource creation (model loading) leaks
     */
    private static void testOneOcrApiOnly(int iterations) throws Exception {
        System.out.println("Testing OneOcrApi creation " + iterations + " times...");
        
        for (int i = 0; i < iterations; i++) {
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);  // This loads the 50MB model
                var processOptions = api.createProcessOptions();
                
                // Create resources but don't do OCR
                processOptions.close();
                pipeline.close(); 
                initOptions.close();
            }
            
            if (i % 10 == 0) {
                long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                System.out.printf("  Iteration %d: JVM Heap = %d MB%n", i, usedMB);
            }
        }
        
        System.out.println("OneOcrApi resource creation test complete - check native memory diff now");
    }
    
    /**
     * Test 3: Full OCR operations (current problematic pattern)
     * This tests the complete current workflow
     */
    private static void testFullOcrOperations(BufferedImage image, int iterations) throws Exception {
        System.out.println("Testing full OCR operations " + iterations + " times...");
        
        for (int i = 0; i < iterations; i++) {
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                // Convert image to BGRA format
                byte[] bgraData = convertToBGRA(image);
                
                // Perform OCR (this should clean up result handles properly)
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Cleanup
                processOptions.close();
                pipeline.close();
                initOptions.close();
            }
            
            if (i % 10 == 0) {
                long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                System.out.printf("  Iteration %d: JVM Heap = %d MB%n", i, usedMB);
            }
        }
        
        System.out.println("Full OCR operations test complete - check native memory diff now");
    }
    
    /**
     * Test 4: Shared resource pattern (like Python reference)
     * Create heavy resources once, reuse for multiple OCR operations
     */
    private static void testSharedResourcePattern(BufferedImage image, int iterations) throws Exception {
        System.out.println("Testing shared resource pattern " + iterations + " operations...");
        
        // Create heavy resources ONCE (Python pattern)
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions); // 50MB model loaded once
            var processOptions = api.createProcessOptions();
            
            // Reuse same resources for multiple OCR operations
            for (int i = 0; i < iterations; i++) {
                byte[] bgraData = convertToBGRA(image);
                
                // Only the OCR operation and result cleanup happen per iteration
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                if (i % 10 == 0) {
                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
                }
            }
            
            // Cleanup heavy resources once at the end
            processOptions.close();
            pipeline.close();
            initOptions.close();
        }
        
        System.out.println("Shared resource pattern test complete - check native memory diff now");
    }
    
    /**
     * Convert BufferedImage to BGRA byte array format expected by OCR API
     */
    private static byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int index = (y * width + x) * 4;
                
                // Extract ARGB components
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                // Convert to BGRA format
                bgraData[index] = (byte) blue;      // B
                bgraData[index + 1] = (byte) green; // G  
                bgraData[index + 2] = (byte) red;   // R
                bgraData[index + 3] = (byte) alpha; // A
            }
        }
        
        return bgraData;
    }
}