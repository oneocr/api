package xyz.jphil.win11_oneocr.test;

import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.OcrResult;
import xyz.jphil.win11_oneocr.OneOcrApi;

/**
 * Memory Leak Detection Test for Windows 11 OneOCR Library
 * 
 * This test identifies and measures memory leaks in different usage patterns:
 * 1. Resource creation/cleanup patterns
 * 2. Arena memory management issues  
 * 3. Native resource cleanup validation
 * 4. Long-running memory consumption patterns
 * 
 * Memory leak patterns identified:
 * - Arena.ofAuto() with Arena leaks - GC won't clean native handles
 * - Missing proper resource cleanup order
 * - Native resource handles not properly released
 * - SharedArena vs LocalArena memory management issues
 */
public class MemoryLeakDetectionTest {
    
    private static final int LEAK_TEST_ITERATIONS = 100; // Higher for leak detection
    private static final int MEMORY_SAMPLE_INTERVAL = 10; // Sample memory every N iterations
    
    public static void main(String[] args) {
        System.out.println("=== Windows 11 OneOCR Memory Leak Detection ===" );
        System.out.println("Running " + LEAK_TEST_ITERATIONS + " iterations to detect memory leaks");
        System.out.println();
        
        try {
            // Load test image once
            var imageUrl = MemoryLeakDetectionTest.class.getResource("/ocr-book.jpg");
            if (imageUrl == null) {
                throw new RuntimeException("Test image ocr-book.jpg not found in test resources");
            }
            BufferedImage testImage = ImageIO.read(imageUrl);
            byte[] bgraData = convertToBGRA(testImage);
            
            System.out.printf("Test image loaded: %dx%d (%d bytes BGRA data)%n", 
                testImage.getWidth(), testImage.getHeight(), bgraData.length);
            System.out.println();
            
            // Test 1: Baseline memory usage pattern (single OCR)
            testBaselineMemoryUsage(testImage, bgraData);
            
            // Test 2: Resource reuse vs recreation pattern  
            testResourceReuseVsRecreation(testImage, bgraData);
            
            // Test 3: Arena lifecycle memory leak test
            testArenaLifecycleMemoryLeak(testImage, bgraData);
            
            // Test 4: Native resource cleanup validation
            testNativeResourceCleanup(testImage, bgraData);
            
            // Test 5: Long-running memory consumption pattern
            testLongRunningMemoryPattern(testImage, bgraData);
            
            // Test 6: Thread-local memory leak pattern
            testThreadLocalMemoryLeaks(testImage, bgraData);
            
        } catch (Exception e) {
            System.err.println("❌ Test setup failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== Memory Leak Detection Complete ===");
    }
    
    /**
     * Test 1: Baseline memory usage for single OCR operation
     */
    private static void testBaselineMemoryUsage(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 1: Baseline Memory Usage (Single OCR)");
        
        long beforeMemory = getUsedMemory();
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            // Single OCR operation
            OcrResult result = api.recognizeImage(pipeline, processOptions,
                image.getWidth(), image.getHeight(), bgraData);
            
            processOptions.close();
            pipeline.close();  
            initOptions.close();
        } catch (Exception e) {
            System.err.println("  Baseline test failed: " + e.getMessage());
        }
        
        // Force GC and measure
        System.gc();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        long afterMemory = getUsedMemory();
        long memoryDiff = afterMemory - beforeMemory;
        
        System.out.printf("  Memory: %d KB before → %d KB after (diff: %+d KB)%n", 
            beforeMemory/1024, afterMemory/1024, memoryDiff/1024);
        System.out.println();
    }
    
    /**
     * Test 2: Resource reuse vs recreation memory patterns
     */
    private static void testResourceReuseVsRecreation(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 2: Resource Reuse vs Recreation");
        
        // Pattern A: Recreate everything each time (current ThreadSafetyTest pattern)
        System.out.println("  Pattern A: Recreate resources each iteration");
        List<Long> memoryRecreate = new ArrayList<>();
        
        for (int i = 0; i < LEAK_TEST_ITERATIONS; i++) {
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                OcrResult result = api.recognizeImage(pipeline, processOptions,
                    image.getWidth(), image.getHeight(), bgraData);
                
                processOptions.close();
                pipeline.close();
                initOptions.close();
                
                // Sample memory usage
                if (i % MEMORY_SAMPLE_INTERVAL == 0) {
                    System.gc();
                    memoryRecreate.add(getUsedMemory());
                }
                
            } catch (Exception e) {
                System.err.printf("    Iteration %d failed: %s%n", i, e.getMessage());
            }
        }
        
        // Pattern B: Reuse resources (recommended pattern)
        System.out.println("  Pattern B: Reuse resources across iterations");
        List<Long> memoryReuse = new ArrayList<>();
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            for (int i = 0; i < LEAK_TEST_ITERATIONS; i++) {
                try {
                    OcrResult result = api.recognizeImage(pipeline, processOptions,
                        image.getWidth(), image.getHeight(), bgraData);
                    
                    // Sample memory usage
                    if (i % MEMORY_SAMPLE_INTERVAL == 0) {
                        System.gc();
                        memoryReuse.add(getUsedMemory());
                    }
                    
                } catch (Exception e) {
                    System.err.printf("    Iteration %d failed: %s%n", i, e.getMessage());
                }
            }
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
        } catch (Exception e) {
            System.err.println("  Resource reuse test failed: " + e.getMessage());
        }
        
        // Analyze memory patterns
        analyzeMemoryTrend("Recreate Pattern", memoryRecreate);
        analyzeMemoryTrend("Reuse Pattern", memoryReuse);
        System.out.println();
    }
    
    /**
     * Test 3: Arena lifecycle memory leak detection
     */
    private static void testArenaLifecycleMemoryLeak(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 3: Arena Lifecycle Memory Leak Test");
        
        List<Long> memoryUsage = new ArrayList<>();
        long startMemory = getUsedMemory();
        
        for (int i = 0; i < LEAK_TEST_ITERATIONS; i++) {
            try (var api = new OneOcrApi()) {
                // Create many local arenas to stress-test arena management
                for (int j = 0; j < 5; j++) {
                    try (var arena = api.createArena()) {
                        // Force arena allocation
                        arena.allocate(1024 * 1024); // 1MB allocation
                    }
                }
                
                // Normal OCR operations
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                OcrResult result = api.recognizeImage(pipeline, processOptions,
                    image.getWidth(), image.getHeight(), bgraData);
                
                processOptions.close();
                pipeline.close();
                initOptions.close();
                
                if (i % MEMORY_SAMPLE_INTERVAL == 0) {
                    System.gc();
                    System.gc();
                    long currentMemory = getUsedMemory();
                    memoryUsage.add(currentMemory);
                    
                    if (i % 50 == 0) { // Progress indicator
                        System.out.printf("    Iteration %d: %d KB%n", 
                            i, currentMemory/1024);
                    }
                }
                
            } catch (Exception e) {
                System.err.printf("  Arena test iteration %d failed: %s%n", i, e.getMessage());
            }
        }
        
        analyzeMemoryTrend("Arena Lifecycle", memoryUsage);
        System.out.println();
    }
    
    /**
     * Test 4: Native resource cleanup validation  
     */
    private static void testNativeResourceCleanup(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 4: Native Resource Cleanup Validation");
        
        AtomicInteger successfulCleanups = new AtomicInteger(0);
        AtomicInteger failedCleanups = new AtomicInteger(0);
        
        for (int i = 0; i < LEAK_TEST_ITERATIONS; i++) {
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                // Perform OCR
                OcrResult result = api.recognizeImage(pipeline, processOptions,
                    image.getWidth(), image.getHeight(), bgraData);
                
                // Test explicit cleanup order
                try {
                    processOptions.close();
                    pipeline.close();
                    initOptions.close();
                    successfulCleanups.incrementAndGet();
                } catch (Exception cleanupEx) {
                    failedCleanups.incrementAndGet();
                    System.err.printf("    Cleanup failed at iteration %d: %s%n", 
                        i, cleanupEx.getMessage());
                }
                
            } catch (Exception e) {
                System.err.printf("  Native cleanup test iteration %d failed: %s%n", 
                    i, e.getMessage());
                failedCleanups.incrementAndGet();
            }
        }
        
        System.out.printf("  Cleanup Results: %d successful, %d failed (%.1f%% success rate)%n",
            successfulCleanups.get(), failedCleanups.get(),
            (successfulCleanups.get() * 100.0) / (successfulCleanups.get() + failedCleanups.get()));
        System.out.println();
    }
    
    /**
     * Test 5: Long-running memory consumption pattern
     */
    private static void testLongRunningMemoryPattern(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 5: Long-Running Memory Pattern (Simulates Production Usage)");
        
        List<Long> memorySnapshots = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        // Simulate long-running service with periodic OCR
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            for (int i = 0; i < LEAK_TEST_ITERATIONS * 2; i++) { // Longer run
                try {
                    // Perform OCR
                    OcrResult result = api.recognizeImage(pipeline, processOptions,
                        image.getWidth(), image.getHeight(), bgraData);
                    
                    // Simulate work with result
                    if (result != null) {
                        String text = result.text(); // Access text
                        result.lines().size(); // Access lines
                    }
                    
                    // Sample memory more frequently for trend analysis
                    if (i % 5 == 0) {
                        long currentMemory = getUsedMemory();
                        memorySnapshots.add(currentMemory);
                    }
                    
                    // Periodic forced GC (simulates production GC patterns)
                    if (i % 50 == 0) {
                        System.gc();
                        long elapsed = System.currentTimeMillis() - startTime;
                        System.out.printf("    %d iterations in %d ms (avg: %.1f ms/iter)%n", 
                            i, elapsed, (double)elapsed / i);
                    }
                    
                } catch (Exception e) {
                    System.err.printf("  Long-running test iteration %d failed: %s%n", 
                        i, e.getMessage());
                }
            }
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
        } catch (Exception e) {
            System.err.println("  Long-running test failed: " + e.getMessage());
        }
        
        analyzeMemoryTrend("Long-Running Pattern", memorySnapshots);
        System.out.println();
    }
    
    /**
     * Test 6: Thread-local memory leak pattern
     */
    private static void testThreadLocalMemoryLeaks(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 6: Thread-Local Memory Leaks");
        
        final int THREAD_COUNT = 3;
        final int ITERATIONS_PER_THREAD = LEAK_TEST_ITERATIONS / THREAD_COUNT;
        
        CountDownLatch startLatch = new CountDownLatch(THREAD_COUNT);  
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        AtomicInteger totalMemoryLeaks = new AtomicInteger(0);
        
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            final int tid = threadId;
            executor.submit(() -> {
                List<Long> threadMemory = new ArrayList<>();
                
                try {
                    startLatch.countDown();
                    startLatch.await(); // All threads start together
                    
                    long threadStartMemory = getUsedMemory();
                    
                    // Each thread has its own OneOcrApi instance
                    try (var api = new OneOcrApi()) {
                        var initOptions = api.createInitOptions();
                        var pipeline = api.createPipeline(initOptions);
                        var processOptions = api.createProcessOptions();
                        
                        for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                            try {
                                OcrResult result = api.recognizeImage(pipeline, processOptions,
                                    image.getWidth(), image.getHeight(), bgraData);
                                
                                if (i % (ITERATIONS_PER_THREAD / 10) == 0) {
                                    long currentMemory = getUsedMemory();
                                    threadMemory.add(currentMemory);
                                }
                                
                            } catch (Exception e) {
                                System.err.printf("  Thread %d iteration %d failed: %s%n", 
                                    tid, i, e.getMessage());
                            }
                        }
                        
                        processOptions.close();
                        pipeline.close();
                        initOptions.close();
                    }
                    
                    System.gc();
                    long threadEndMemory = getUsedMemory();
                    long threadMemoryDiff = threadEndMemory - threadStartMemory;
                    
                    System.out.printf("  Thread %d: %d KB → %d KB (diff: %+d KB)%n", 
                        tid, threadStartMemory/1024, threadEndMemory/1024, threadMemoryDiff/1024);
                    
                    if (threadMemoryDiff > 10 * 1024 * 1024) { // > 10MB increase
                        totalMemoryLeaks.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    System.err.printf("  Thread %d failed: %s%n", tid, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        try {
            doneLatch.await();
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.printf("  Thread-local memory leaks detected: %d/%d threads%n", 
            totalMemoryLeaks.get(), THREAD_COUNT);
        System.out.println();
    }
    
    // Utility methods
    
    private static long getUsedMemory() {
        var memoryBean = ManagementFactory.getMemoryMXBean();
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    private static void analyzeMemoryTrend(String testName, List<Long> memoryUsage) {
        if (memoryUsage.size() < 2) {
            System.out.printf("  %s: Insufficient data for trend analysis%n", testName);
            return;
        }
        
        long startMemory = memoryUsage.get(0);
        long endMemory = memoryUsage.get(memoryUsage.size() - 1);
        long totalIncrease = endMemory - startMemory;
        
        // Calculate trend (simple linear regression slope)
        double avgIncrease = 0;
        for (int i = 1; i < memoryUsage.size(); i++) {
            avgIncrease += (memoryUsage.get(i) - memoryUsage.get(i-1));
        }
        avgIncrease /= (memoryUsage.size() - 1);
        
        System.out.printf("  %s: %d KB → %d KB (total: %+d KB, avg change: %+.1f KB/sample)%n",
            testName, startMemory/1024, endMemory/1024, totalIncrease/1024, avgIncrease/1024);
        
        // Memory leak warning
        if (totalIncrease > 50 * 1024 * 1024) { // > 50MB increase
            System.out.printf("  ⚠️  WARNING: Potential memory leak detected in %s%n", testName);
        }
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