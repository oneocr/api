/**
 * Windows 11 OneOCR Java API Module
 * 
 * Provides Java Foreign Function & Memory (FFM) bindings for Windows 11's built-in OCR engine.
 * This module enables text extraction from images using the same OCR technology that powers
 * Windows 11's Snipping Tool.
 * 
 * @since 1.0
 */
module xyz.jphil.win11_oneocr {
    
    // Core Java modules required for FFM
    requires java.base;
    requires java.desktop;  // For BufferedImage support in examples/tests
    requires java.management;  // For memory monitoring in leak detection tests
    
    // Export main API packages
    exports xyz.jphil.win11_oneocr;
}