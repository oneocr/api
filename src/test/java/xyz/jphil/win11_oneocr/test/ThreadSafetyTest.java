package xyz.jphil.win11_oneocr.test;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.OcrResult;
import xyz.jphil.win11_oneocr.OneOcrApi;

/**
 * Thread Safety Test for Windows 11 OneOCR Library
 * 
 * This test validates different threading scenarios to understand the exact 
 * limitations and find the optimal approach for multi-threaded OCR processing.
 * 
 * Test Scenarios:
 * 1. Sequential single-threaded (baseline)
 * 2. Multiple OneOcrApi instances, concurrent access
 * 3. Single shared OneOcrApi instance, synchronized access  
 * 4. Multiple instances with staggered initialization
 * 5. Resource cleanup timing tests
 */
public class ThreadSafetyTest {
    
    private static final int TEST_ITERATIONS = 10;
    private static final int THREAD_COUNT = 3;
    
    public static void main(String[] args) {
        System.out.println("=== Windows 11 OneOCR Thread Safety Analysis ===");
        System.out.println("Testing with " + THREAD_COUNT + " threads, " + TEST_ITERATIONS + " iterations each");
        System.out.println();
        
        try {
            // Load test image once
            var imageUrl = ThreadSafetyTest.class.getResource("/ocr-book.jpg");
            if (imageUrl == null) {
                throw new RuntimeException("Test image ocr-book.jpg not found in test resources");
            }
            BufferedImage testImage = ImageIO.read(imageUrl);
            byte[] bgraData = convertToBGRA(testImage);
            
            System.out.printf("Test image loaded: %dx%d (%d bytes BGRA data)%n", 
                testImage.getWidth(), testImage.getHeight(), bgraData.length);
            System.out.println();
            
            // Test 1: Sequential baseline (should always work)
            testSequentialBaseline(testImage, bgraData);
            
            // Test 2: Multiple instances, concurrent access (expected to fail)
            testMultipleInstancesConcurrent(testImage, bgraData);
            
            // Test 3: Single shared instance, synchronized access
            testSingleInstanceSynchronized(testImage, bgraData);
            
            // Test 4: Multiple instances with staggered start
            testMultipleInstancesStaggered(testImage, bgraData);
            
            // Test 5: Resource cleanup timing
            testResourceCleanupTiming(testImage, bgraData);
            
        } catch (Exception e) {
            System.err.println("❌ Test setup failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== Thread Safety Analysis Complete ===");
    }
    
    /**
     * Test 1: Sequential single-threaded processing (baseline)
     */
    private static void testSequentialBaseline(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 1: Sequential Single-Threaded (Baseline)");
        
        int successCount = 0;
        long totalTime = 0;
        
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long start = System.currentTimeMillis();
                try {
                    OcrResult result = api.recognizeImage(pipeline, processOptions, 
                        image.getWidth(), image.getHeight(), bgraData);
                    
                    if (result != null && !result.text().trim().isEmpty()) {
                        successCount++;
                    }
                    totalTime += (System.currentTimeMillis() - start);
                    
                } catch (Exception e) {
                    System.err.printf("  Iteration %d failed: %s%n", i+1, e.getMessage());
                }
            }
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
        } catch (Exception e) {
            System.err.println("  Setup failed: " + e.getMessage());
        }
        
        System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
            successCount, TEST_ITERATIONS, (successCount * 100.0) / TEST_ITERATIONS);
        System.out.printf("  Average time: %dms per OCR%n", totalTime / TEST_ITERATIONS);
        System.out.println();
    }
    
    /**
     * Test 2: Multiple OneOcrApi instances, concurrent access (expected to fail)
     */
    private static void testMultipleInstancesConcurrent(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 2: Multiple Instances, Concurrent Access");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            final int tid = threadId;
            executor.submit(() -> {
                try (var api = new OneOcrApi()) {
                    var initOptions = api.createInitOptions();
                    var pipeline = api.createPipeline(initOptions);
                    var processOptions = api.createProcessOptions();
                    
                    startLatch.countDown();
                    startLatch.await(); // All threads start simultaneously
                    
                    for (int i = 0; i < TEST_ITERATIONS; i++) {
                        totalAttempts.incrementAndGet();
                        try {
                            OcrResult result = api.recognizeImage(pipeline, processOptions,
                                image.getWidth(), image.getHeight(), bgraData);
                            
                            if (result != null && !result.text().trim().isEmpty()) {
                                successCount.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            System.err.printf("  Thread %d, iteration %d failed: %s%n", 
                                tid, i+1, e.getMessage());
                        }
                    }
                    
                    processOptions.close();
                    pipeline.close();
                    initOptions.close();
                    
                } catch (Exception e) {
                    System.err.printf("  Thread %d setup failed: %s%n", tid, e.getMessage());
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
        
        System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
            successCount.get(), totalAttempts.get(), 
            (successCount.get() * 100.0) / totalAttempts.get());
        System.out.println();
    }
    
    /**
     * Test 3: Single shared OneOcrApi instance, synchronized access
     */
    private static void testSingleInstanceSynchronized(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 3: Single Shared Instance, Synchronized Access");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        try (var sharedApi = new OneOcrApi()) {
            var initOptions = sharedApi.createInitOptions();
            var pipeline = sharedApi.createPipeline(initOptions);
            var processOptions = sharedApi.createProcessOptions();
            
            final Object ocrLock = new Object();
            
            for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                final int tid = threadId;
                executor.submit(() -> {
                    try {
                        startLatch.countDown();
                        startLatch.await(); // All threads start simultaneously
                        
                        for (int i = 0; i < TEST_ITERATIONS; i++) {
                            totalAttempts.incrementAndGet();
                            try {
                                synchronized (ocrLock) {
                                    OcrResult result = sharedApi.recognizeImage(pipeline, processOptions,
                                        image.getWidth(), image.getHeight(), bgraData);
                                    
                                    if (result != null && !result.text().trim().isEmpty()) {
                                        successCount.incrementAndGet();
                                    }
                                }
                                
                            } catch (Exception e) {
                                System.err.printf("  Thread %d, iteration %d failed: %s%n", 
                                    tid, i+1, e.getMessage());
                            }
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
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
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
            
        } catch (Exception e) {
            System.err.println("  Shared instance setup failed: " + e.getMessage());
        }
        
        System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
            successCount.get(), totalAttempts.get(), 
            (successCount.get() * 100.0) / totalAttempts.get());
        System.out.println();
    }
    
    /**
     * Test 4: Multiple instances with staggered initialization
     */
    private static void testMultipleInstancesStaggered(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 4: Multiple Instances, Staggered Start");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            final int tid = threadId;
            executor.submit(() -> {
                try {
                    // Stagger thread starts by 100ms each
                    Thread.sleep(tid * 100);
                    
                    try (var api = new OneOcrApi()) {
                        var initOptions = api.createInitOptions();
                        var pipeline = api.createPipeline(initOptions);
                        var processOptions = api.createProcessOptions();
                        
                        for (int i = 0; i < TEST_ITERATIONS; i++) {
                            totalAttempts.incrementAndGet();
                            try {
                                OcrResult result = api.recognizeImage(pipeline, processOptions,
                                    image.getWidth(), image.getHeight(), bgraData);
                                
                                if (result != null && !result.text().trim().isEmpty()) {
                                    successCount.incrementAndGet();
                                }
                                
                            } catch (Exception e) {
                                System.err.printf("  Thread %d, iteration %d failed: %s%n", 
                                    tid, i+1, e.getMessage());
                            }
                        }
                        
                        processOptions.close();
                        pipeline.close();
                        initOptions.close();
                        
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
        
        System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
            successCount.get(), totalAttempts.get(), 
            (successCount.get() * 100.0) / totalAttempts.get());
        System.out.println();
    }
    
    /**
     * Test 5: Resource cleanup timing effects
     */
    private static void testResourceCleanupTiming(BufferedImage image, byte[] bgraData) {
        System.out.println("TEST 5: Resource Cleanup Timing Test");
        
        int successCount = 0;
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                try {
                    OcrResult result = api.recognizeImage(pipeline, processOptions,
                        image.getWidth(), image.getHeight(), bgraData);
                    
                    if (result != null && !result.text().trim().isEmpty()) {
                        successCount++;
                    }
                    
                } catch (Exception e) {
                    System.err.printf("  Iteration %d failed: %s%n", i+1, e.getMessage());
                } finally {
                    processOptions.close();
                    pipeline.close();
                    initOptions.close();
                }
                
                // Force brief pause between complete resource cycles
                Thread.sleep(10);
                
            } catch (Exception e) {
                System.err.printf("  Resource setup %d failed: %s%n", i+1, e.getMessage());
            }
        }
        
        System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
            successCount, TEST_ITERATIONS, (successCount * 100.0) / TEST_ITERATIONS);
        System.out.println();
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