package xyz.jphil.win11_oneocr.test;

import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.*;

/**
 * Progressive Cleanup Test - Find ways to mitigate the 3-4MB per OCR operation leak
 * 
 * Tests different cleanup patterns to see if we can force native memory release:
 * 1. Standard pattern (baseline leak)
 * 2. Pipeline recreation per operation 
 * 3. Full API recreation per operation
 * 4. System.gc() + manual cleanup triggers
 * 5. Process options recreation
 * 6. Native library reloading
 * 
 * Run with: -XX:NativeMemoryTracking=detail -Xmx1g
 * Monitor with: jcmd <pid> VM.native_memory summary.diff scale=MB
 */
public class ProgressiveCleanupTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Progressive Cleanup Test ===");
        System.out.println("Process PID: " + ProcessHandle.current().pid());
        System.out.println("JVM Max Heap: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println("\nRun: jcmd " + ProcessHandle.current().pid() + " VM.native_memory baseline");
        System.out.println("Then: jcmd " + ProcessHandle.current().pid() + " VM.native_memory summary.diff scale=MB");
        
        // Load test image once
        BufferedImage testImage = ImageIO.read(
            ProgressiveCleanupTest.class.getResourceAsStream("/ocr-book.jpg"));
        System.out.println("Test image loaded: " + testImage.getWidth() + "x" + testImage.getHeight());
        
        System.out.println("\n=== BASELINE - Set jcmd baseline now ===");
        Thread.sleep(5000);
        
        // Test 1: Standard Pattern (baseline leak)
        System.out.println("\n=== TEST 1: Standard Pattern (Baseline Leak) ===");
        testStandardPattern(testImage, 20);
        System.gc();
        Thread.sleep(2000);
        
        // Test 2: Pipeline Recreation Per Operation
        System.out.println("\n=== TEST 2: Pipeline Recreation Per Operation ===");
        testPipelineRecreation(testImage, 20);
        System.gc();
        Thread.sleep(2000);
        
        // Test 3: Full API Recreation Per Operation  
        System.out.println("\n=== TEST 3: Full API Recreation Per Operation ===");
        testFullApiRecreation(testImage, 20);
        System.gc();
        Thread.sleep(2000);
        
        // Test 4: Aggressive GC + Manual Cleanup
        System.out.println("\n=== TEST 4: Aggressive GC + Manual Cleanup ===");
        testAggressiveCleanup(testImage, 20);
        System.gc();
        Thread.sleep(2000);
        
        // Test 5: Process Options Recreation
        System.out.println("\n=== TEST 5: Process Options Recreation ===");
        testProcessOptionsRecreation(testImage, 20);
        System.gc();
        Thread.sleep(2000);
        
        // Test 6: Native Library Reloading
        System.out.println("\n=== TEST 6: Native Library Reloading ===");
        testNativeLibraryReloading(testImage, 10); // Fewer iterations as this is expensive
        System.gc();
        Thread.sleep(2000);
        
        System.out.println("\n=== ANALYSIS COMPLETE ===");
        System.out.println("Check native memory diff after each test phase");
        System.out.println("The test with smallest memory growth may provide mitigation strategy");
    }
    
    /**
     * Test 1: Standard Pattern (Shared resources, reuse everything)
     * This should show the baseline 3-4MB per operation leak
     */
    private static void testStandardPattern(BufferedImage image, int operations) throws Exception {
        System.out.println("Testing standard pattern " + operations + " operations...");
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            for (int i = 0; i < operations; i++) {
                byte[] bgraData = convertToBGRA(image);
                
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                if (i % 5 == 0) {
                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
                }
            }
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
        }
        
        System.out.println("Standard pattern test complete - check native memory diff now");
    }
    
    /**
     * Test 2: Pipeline Recreation Per Operation
     * Hypothesis: Maybe recreating the pipeline clears model internal state
     */
    private static void testPipelineRecreation(BufferedImage image, int operations) throws Exception {
        System.out.println("Testing pipeline recreation " + operations + " operations...");
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            
            for (int i = 0; i < operations; i++) {
                // Recreate pipeline and process options for each operation
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                byte[] bgraData = convertToBGRA(image);
                
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Cleanup immediately after each operation
                processOptions.close();
                pipeline.close();
                
                if (i % 5 == 0) {
                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
                }
            }
            
            initOptions.close();
        }
        
        System.out.println("Pipeline recreation test complete - check native memory diff now");
    }
    
    /**
     * Test 3: Full API Recreation Per Operation
     * Hypothesis: Complete resource refresh might clear native state
     */
    private static void testFullApiRecreation(BufferedImage image, int operations) throws Exception {
        System.out.println("Testing full API recreation " + operations + " operations...");
        
        for (int i = 0; i < operations; i++) {
            // Recreate everything for each operation
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                byte[] bgraData = convertToBGRA(image);
                
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                processOptions.close();
                pipeline.close();
                initOptions.close();
            }
            
            if (i % 5 == 0) {
                long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
            }
        }
        
        System.out.println("Full API recreation test complete - check native memory diff now");
    }
    
    /**
     * Test 4: Aggressive GC + Manual Cleanup
     * Hypothesis: Force GC and explicit cleanup calls might help
     */
    private static void testAggressiveCleanup(BufferedImage image, int operations) throws Exception {
        System.out.println("Testing aggressive cleanup " + operations + " operations...");
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            for (int i = 0; i < operations; i++) {
                byte[] bgraData = convertToBGRA(image);
                
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Aggressive cleanup after each operation
                System.gc();
                System.runFinalization();
                Thread.sleep(100); // Give GC time to work
                
                if (i % 5 == 0) {
                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
                }
            }
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
        }
        
        System.out.println("Aggressive cleanup test complete - check native memory diff now");
    }
    
    /**
     * Test 5: Process Options Recreation
     * Hypothesis: Process options might hold internal state that accumulates
     */
    private static void testProcessOptionsRecreation(BufferedImage image, int operations) throws Exception {
        System.out.println("Testing process options recreation " + operations + " operations...");
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            
            for (int i = 0; i < operations; i++) {
                // Recreate only process options for each operation
                var processOptions = api.createProcessOptions();
                
                byte[] bgraData = convertToBGRA(image);
                
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Cleanup process options immediately
                processOptions.close();
                
                if (i % 5 == 0) {
                    long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                    System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
                }
            }
            
            pipeline.close();
            initOptions.close();
        }
        
        System.out.println("Process options recreation test complete - check native memory diff now");
    }
    
    /**
     * Test 6: Native Library Reloading
     * Hypothesis: Completely reloading the native library might clear all state
     * This is the most expensive test but might reveal if DLL reloading helps
     */
    private static void testNativeLibraryReloading(BufferedImage image, int operations) throws Exception {
        System.out.println("Testing native library reloading " + operations + " operations...");
        System.out.println("WARNING: This test recreates LoadNativeLib per operation - very expensive!");
        
        for (int i = 0; i < operations; i++) {
            // Completely reload native library for each operation
            try (var api = new OneOcrApi()) { // This recreates LoadNativeLib internally
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                byte[] bgraData = convertToBGRA(image);
                
                OcrResult result = api.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                
                processOptions.close();
                pipeline.close();
                initOptions.close();
            }
            
            // Extra cleanup time for native library unloading
            System.gc();
            System.runFinalization();
            Thread.sleep(200);
            
            if (i % 2 == 0) { // Report more frequently due to fewer operations
                long usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
                System.out.printf("  Operation %d: JVM Heap = %d MB%n", i, usedMB);
            }
        }
        
        System.out.println("Native library reloading test complete - check native memory diff now");
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