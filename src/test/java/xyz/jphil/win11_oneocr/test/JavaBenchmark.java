package xyz.jphil.win11_oneocr.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import xyz.jphil.win11_oneocr.OcrResult;
import xyz.jphil.win11_oneocr.OneOcrApi;

/**
 * Performance benchmark for Java OCR - similar to C++ benchmark_ocr.cpp
 */
public class JavaBenchmark {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java JavaBenchmark <image-path> [iterations]");
            return;
        }
        
        String imagePath = args[0];
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        
        Path dllPath = Path.of("oneocr.dll");
        Path modelPath = Path.of("oneocr.onemodel");
        
        System.out.println("=== Java OCR Benchmark ===");
        System.out.println("Image: " + imagePath);
        System.out.println("Iterations: " + iterations);
        System.out.println();
        
        try {
            // Load image once
            BufferedImage image = ImageIO.read(new File(imagePath));
            byte[] bgraData = convertToBGRA(image);
            System.out.printf("Image loaded: %dx%d%n", image.getWidth(), image.getHeight());
            
            // Initialize OCR API (model loaded once)
            try (var api = new OneOcrApi()) {
                var initOptions = api.createInitOptions();
                var pipeline = api.createPipeline(initOptions);
                var processOptions = api.createProcessOptions();
                
                System.out.println("OCR model loaded successfully...");
                
                // Warmup
                System.out.print("Warmup... ");
                api.recognizeImage(pipeline, processOptions, image.getWidth(), image.getHeight(), bgraData);
                System.out.println("done");
                
                // Benchmark
                System.out.println("\\nStarting benchmark...");
                long startTime = System.nanoTime();
                
                for (int i = 0; i < iterations; i++) {
                    long iterStart = System.nanoTime();
                    
                    OcrResult result = api.recognizeImage(pipeline, processOptions, image.getWidth(), image.getHeight(), bgraData);
                    
                    long iterEnd = System.nanoTime();
                    double iterTimeMs = (iterEnd - iterStart) / 1_000_000.0;
                    
                    System.out.printf("Iteration %d: %.2f ms, %d lines, %d words%n", 
                        i + 1, iterTimeMs, result.lines().size(), 
                        result.lines().stream().mapToInt(line -> line.words().size()).sum());
                }
                
                long endTime = System.nanoTime();
                double totalTimeS = (endTime - startTime) / 1_000_000_000.0;
                double avgTimePerImage = totalTimeS / iterations;
                double imagesPerMinute = 60.0 / avgTimePerImage;
                
                System.out.println();
                System.out.println("=== Performance Results ===");
                System.out.printf("Total time: %.2f seconds%n", totalTimeS);
                System.out.printf("Average time per image: %.3f seconds%n", avgTimePerImage);
                System.out.printf("Images per minute: %.1f%n", imagesPerMinute);
                System.out.printf("Throughput: %.1f images/minute%n", imagesPerMinute);
                
                // Cleanup
                processOptions.close();
                pipeline.close();
                initOptions.close();
                
                // Compare with C++ benchmark results
                System.out.println();
                System.out.println("=== Comparison with C++ ===");
                System.out.println("C++ performance: ~38.3 images/minute (1.56s per image)");
                double javaSpeedup = imagesPerMinute / 38.3;
                if (javaSpeedup > 1.0) {
                    System.out.printf("Java is %.1fx FASTER than C++%n", javaSpeedup);
                } else {
                    System.out.printf("Java is %.1fx slower than C++%n", 1.0 / javaSpeedup);
                }
                
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Convert BufferedImage to BGRA byte array
     */
    private static byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int offset = (y * width + x) * 4;
                
                // Extract ARGB components
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                
                // Store as BGRA
                bgraData[offset] = (byte) blue;     // B
                bgraData[offset + 1] = (byte) green; // G
                bgraData[offset + 2] = (byte) red;   // R
                bgraData[offset + 3] = (byte) alpha; // A
            }
        }
        
        return bgraData;
    }
}