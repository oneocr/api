package xyz.jphil.win11_oneocr.test;

import xyz.jphil.win11_oneocr.OneOcrApi;
import xyz.jphil.win11_oneocr.OneOcrApi;

/**
 * Simple test to verify MethodHandle compatibility between JDK versions
 */
public class MethodHandleTest {
    
    public static void main(String[] args) {
        try {
            System.out.printf("Testing on Java %s%n", System.getProperty("java.version"));
            
            // Test OneOcrApi initialization (this will test the MethodHandles)
            var api = new OneOcrApi();
            System.out.println("✅ OneOcrApi initialization successful!");
            
            // Test basic operations
            var initOptions = api.createInitOptions();
            System.out.println("✅ createInitOptions successful!");
            
            var pipeline = api.createPipeline(initOptions);
            System.out.println("✅ createPipeline successful!");
            
            var processOptions = api.createProcessOptions();
            System.out.println("✅ createProcessOptions successful!");
            
            // Cleanup
            processOptions.close();
            pipeline.close();
            initOptions.close();
            api.close();
            
            System.out.println("✅ All MethodHandle operations successful on JDK " + System.getProperty("java.version"));
            
        } catch (Exception e) {
            System.err.println("❌ MethodHandle test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}