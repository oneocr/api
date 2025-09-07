package xyz.jphil.win11_oneocr;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;
import static xyz.jphil.win11_oneocr.OneOcrHomeDir.findPath;
import static xyz.jphil.win11_oneocr.OcrWord.ocrWord;
import static xyz.jphil.win11_oneocr.Utils.*;

/**
 * Comprehensive Java FFM binding for Windows 11 SnippingTool OCR (oneocr.dll)
 * 
 * CRITICAL REFACTORING WARNING:
 * ============================ 
 * DO NOT REFACTOR THIS CLASS WITHOUT FIRST READING THE PYTHON REFERENCE IMPLEMENTATION!
 * 
 * This implementation is based on the Python ctypes binding from reference projects.
 * 
 * The Python file 'oneocr_reference.py' in the project root contains the authoritative 
 * reference implementation that this Java code follows. All function signatures, parameter
 * types, calling conventions, and memory layouts MUST match the Python implementation.
 * 
 * Key implementation details derived from Python reference:
 * - All DLL functions return int (0 = success, non-zero = error)  
 * - Most functions use output parameters (passed by reference)
 * - Image structure layout matches Python ImageStructure exactly
 * - Function signatures must match Python DLL_FUNCTIONS list exactly
 * - Error handling follows Python _check_dll_result pattern
 * - Configuration calls (SetUseModelDelayLoad, SetMaxRecognitionLineCount) are mandatory
 * 
 * Before making ANY changes to function signatures or calling conventions:
 * 1. Read oneocr_reference.py in the project root
 * 2. Verify changes against Python ctypes declarations  
 * 
 * Java FFM port with cross-JDK compatibility by Claude Code
 */
public class OneOcrApi implements AutoCloseable {
    // Function handles - lazy loaded
    private final LoadNativeLib lib;

    public OneOcrApi() throws IOException {
        lib = new LoadNativeLib();
        lib.initializeFunctionHandles();
    }    
    
    
    // High-level API methods
    
    public OcrInitOptions createInitOptions() {
        // Python reference (oneocr_reference.py):
        // def _create_init_options(self):
        //     init_options = c_int64()
        //     self._check_dll_result(
        //         ocr_dll.CreateOcrInitOptions(byref(init_options)),
        //         'Init options creation failed'
        //     )
        //     
        //     self._check_dll_result(
        //         ocr_dll.OcrInitOptionsSetUseModelDelayLoad(init_options, 0),
        //         'Model loading config failed'
        //     )
        //     return init_options
        
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        try {
            var handlePtr = tempArena.allocate(JAVA_LONG);
            int result = lib.CreateOcrInitOptions(handlePtr);
            if (result != 0) {
                throw new RuntimeException("CreateOcrInitOptions failed with code: " + result);
            }
            long handle = handlePtr.get(JAVA_LONG, 0);
            
            // Set model delay load to false (0) like Python code
            int delayResult = lib.OcrInitOptionsSetUseModelDelayLoad(handle, 0);
            if (delayResult != 0) {
                throw new RuntimeException("OcrInitOptionsSetUseModelDelayLoad failed with code: " + delayResult);
            }
            
            return new OcrInitOptions(handle, lib);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create OCR init options", t);
        }
        // tempArena and its allocations become GC-eligible when method returns
    }
    
    public OcrPipeline createPipeline(OcrInitOptions initOptions) {
        // C++ reference (ocr.cpp):
        // const char *key = {"kj)TGtrK>f]b[Piow.gU+nC@s\"\"\"\"\"\"\""};
        // res = CreateOcrPipeline((__int64)"oneocr.onemodel", (__int64)key, ctx, &pipeline);
        // assert(res == 0);
        //
        // Python reference (oneocr_reference.py):
        // def _create_pipeline(self):
        //     model_path = os.path.join(CONFIG_DIR, MODEL_NAME)
        //     model_buf = ctypes.create_string_buffer(model_path.encode())
        //     key_buf = ctypes.create_string_buffer(MODEL_KEY)
        //
        //     pipeline = c_int64()
        //     with suppress_output():
        //         self._check_dll_result(
        //             ocr_dll.CreateOcrPipeline(
        //                 model_buf,
        //                 key_buf,
        //                 self.init_options,
        //                 byref(pipeline)
        //             ),
        //             'Pipeline creation failed'
        //         )
        //     return pipeline
        // 
        // MODEL_KEY = b"kj)TGtrK>f]b[Piow.gU+nC@s\"\"\"\"\"\"4"
        
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        try {
            // Find model file - look for oneocr.onemodel
            // Model file is now managed by ensureNativesExtracted()
            var modelPath = findPath("oneocr.onemodel")
                .orElseThrow(()->new RuntimeException("Could not find oneocr.onemodel file"))
                .toAbsolutePath().toString();
            
            var modelPathStr = allocateString(tempArena,modelPath);
            var modelKey = allocateString(tempArena,"kj)TGtrK>f]b[Piow.gU+nC@s\"\"\"\"\"\"4");
            var pipelinePtr = tempArena.allocate(JAVA_LONG);
            
            int result = lib.CreateOcrPipeline(modelPathStr, modelKey, initOptions.getHandle(), pipelinePtr);
            if (result != 0) {
                throw new RuntimeException("CreateOcrPipeline failed with code: " + result);
            }
            
            long handle = pipelinePtr.get(JAVA_LONG, 0);
            return new OcrPipeline(handle, lib);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create OCR pipeline", t);
        }
    }
    
    /**
     * Create OCR process options with default max recognition line count of 1000.
     * This is the standard value used by all C++ and Python reference implementations.
     * 
     * @return configured OcrProcessOptions instance
     */
    public OcrProcessOptions createProcessOptions() {
        return createProcessOptions(1000); // Default value from all reference implementations
    }
    
    /**
     * Create OCR process options with custom max recognition line count.
     * 
     * @param maxRecognitionLineCount maximum number of text lines to recognize (typically 1000)
     * @return configured OcrProcessOptions instance
     */
    public OcrProcessOptions createProcessOptions(int maxRecognitionLineCount) {
        // Python reference (oneocr_reference.py):
        // def _create_process_options(self):
        //     process_options = c_int64()
        //     self._check_dll_result(
        //         ocr_dll.CreateOcrProcessOptions(byref(process_options)),
        //         'Process options creation failed'
        //     )
        //     
        //     self._check_dll_result(
        //         ocr_dll.OcrProcessOptionsSetMaxRecognitionLineCount(
        //             process_options, 1000),
        //         'Line count config failed'
        //     )
        //     return process_options
        //
        // C++ reference (ocr.cpp):
        // res = OcrProcessOptionsSetMaxRecognitionLineCount(opt, 1000);
        // assert(res == 0);
        
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        try {
            var processOptionsPtr = tempArena.allocate(JAVA_LONG);
            int result = lib.CreateOcrProcessOptions(processOptionsPtr);
            if (result != 0) {
                throw new RuntimeException("CreateOcrProcessOptions failed with code: " + result);
            }
            long handle = processOptionsPtr.get(JAVA_LONG, 0);
            
            // Set max recognition line count (default 1000 matches all reference implementations)
            int lineCountResult = lib.OcrProcessOptionsSetMaxRecognitionLineCount(handle, maxRecognitionLineCount);
            if (lineCountResult != 0) {
                throw new RuntimeException("OcrProcessOptionsSetMaxRecognitionLineCount failed with code: " + lineCountResult);
            }
            
            return new OcrProcessOptions(handle, lib);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create OCR process options", t);
        }
    }
    
    /**
     * Perform OCR on BGRA image data
     * @param pipeline OCR pipeline
     * @param options Process options
     * @param width Image width in pixels
     * @param height Image height in pixels  
     * @param imageData BGRA pixel data (4 bytes per pixel)
     * @return OCR result with text, angle and bounding boxes
     */
    public OcrResult recognizeImage(OcrPipeline pipeline, OcrProcessOptions options, int width, int height, byte[] imageData) {
        // Python reference (oneocr_reference.py):
        // def _process_image(self, cols, rows, step, data):
        //     '''Create image structure'''
        //     if isinstance(data, bytes):
        //         data_ptr = (c_ubyte * len(data)).from_buffer_copy(data)
        //     else:
        //         data_ptr = ctypes.cast(ctypes.c_void_p(data), c_ubyte_p)
        //     
        //     img_struct = ImageStructure(
        //         type=3,
        //         width=cols,
        //         height=rows,
        //         _reserved=0,
        //         step_size=step,
        //         data_ptr=data_ptr
        //     )
        //
        //     return self._perform_ocr(img_struct)
        //
        // def _perform_ocr(self, image_struct):
        //     '''Execute OCR pipeline and parse results'''
        //     ocr_result = c_int64()
        //     if ocr_dll.RunOcrPipeline(
        //             self.pipeline,
        //             byref(image_struct),
        //             self.process_options,
        //             byref(ocr_result)
        //         ) != 0:
        //         return self.empty_result
        
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        try {
            // Create image structure - match C++ Img struct exactly (32 bytes)
            var imageStruct = tempArena.allocate(32);
            
            // Allocate image data
            var imageDataPtr = tempArena.allocate(imageData.length);
            // TODO: optimize to avoid copy (might be difficult for now as image is originating in pure java. However it might work had we used nio .... or perhaps they would provide suchs optimization in jdk in future where byte[] are easily mappable to native given developments in proj. amber)
            MemorySegment.copy(MemorySegment.ofArray(imageData), 0, imageDataPtr, 0, imageData.length);
            
            // Fill image structure
            imageStruct.set(JAVA_INT, 0, 3);                   // CV_8UC4
            imageStruct.set(JAVA_INT, 4, width);               // width
            imageStruct.set(JAVA_INT, 8, height);              // height  
            imageStruct.set(JAVA_INT, 12, 0);                  // reserved
            imageStruct.set(JAVA_LONG, 16, width * 4L);        // step
            imageStruct.set(JAVA_LONG, 24, imageDataPtr.address()); // data_ptr as long
            
            // Run OCR - RunOcrPipeline(pipeline, image_struct_ptr, process_options, result_ptr) -> int
            var resultPtr = tempArena.allocate(JAVA_LONG);
            int result = lib.RunOcrPipeline(
                pipeline.getHandle(), imageStruct, options.getHandle(), resultPtr);
            
            if (result != 0) {
                throw new RuntimeException("RunOcrPipeline failed with code: " + result);
            }
            
            long resultHandle = resultPtr.get(JAVA_LONG, 0);
                
            return parseOcrResult(resultHandle);
            
        } catch (Throwable t) {
            throw new RuntimeException("Failed to recognize image data", t);
        }
    }
    
    private OcrResult parseOcrResult(long resultHandle) {
        if (resultHandle == 0) {
            return new OcrResult("", 0.0f, List.of());
        }
        
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        try {
            // Get line count using output parameter
            var lineCountPtr = tempArena.allocate(JAVA_LONG);
            int result = lib.GetOcrLineCount(resultHandle, lineCountPtr);
            if (result != 0) {
                return new OcrResult("", 0.0f, List.of());
            }
            int lineCount = (int) lineCountPtr.get(JAVA_LONG, 0);
            List<OcrLine> lines = new ArrayList<>();
            
            // Get text angle using output parameter
            double textAngle = 0.0;
            try {
                var anglePtr = tempArena.allocate(JAVA_FLOAT);
                int angleResult = lib.GetImageAngle(resultHandle, anglePtr);
                if (angleResult == 0) {
                    textAngle = anglePtr.get(JAVA_FLOAT, 0);
                }
            } catch (Throwable ignore) {
                // Angle might not be available for all result types
            }
            
            for (int i = 0; i < lineCount; i++) {
                var lineHandlePtr = tempArena.allocate(JAVA_LONG);
                int lineResult = lib.GetOcrLine(resultHandle, i, lineHandlePtr);
                if (lineResult == 0) {
                    long lineHandle = lineHandlePtr.get(JAVA_LONG, 0);
                    OcrLine line = parseOcrLine(lineHandle);
                    lines.add(line);
                }
            }
            
            // Combine all line texts
            var fullText = lines.stream()
                    .map(OcrLine::text)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            
            return new OcrResult(fullText, textAngle, lines);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to parse OCR result", t);
        } finally {
            try {
                lib.ReleaseOcrResult(resultHandle);
            } catch (Throwable ignore) {}
        }
    }
    
    private OcrLine parseOcrLine(long lineHandle) throws Throwable {
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        
        // Get line content using output parameter
        var contentPtrPtr = tempArena.allocate(ADDRESS);
        var lineText = "";
        int contentResult = lib.GetOcrLineContent(lineHandle, contentPtrPtr);
        if (contentResult == 0) {
            var contentPtr = contentPtrPtr.get(ADDRESS, 0);
            if (!contentPtr.equals(MemorySegment.NULL)) {
                lineText = readString(contentPtr.address());
            }
        }
        
        // Get line bounding box using output parameter  
        var bboxPtrPtr = tempArena.allocate(ADDRESS);
        BoundingBox lineBBox = null;
        int bboxResult = lib.GetOcrLineBoundingBox(lineHandle, bboxPtrPtr);
        if (bboxResult == 0) {
            var bboxPtr = bboxPtrPtr.get(ADDRESS, 0);
            if (!bboxPtr.equals(MemorySegment.NULL)) {
                lineBBox = readBoundingBox(bboxPtr.address());
            }
        }
        
        // Get words in line using output parameter
        var wordCountPtr = tempArena.allocate(JAVA_LONG);
        int wordCountResult = lib.GetOcrLineWordCount(lineHandle, wordCountPtr);
        List<OcrWord> words = new ArrayList<>();
        
        if (wordCountResult == 0) {
            int wordCount = (int) wordCountPtr.get(JAVA_LONG, 0);
            
            for (int j = 0; j < wordCount; j++) {
                var wordHandlePtr = tempArena.allocate(JAVA_LONG);
                int wordResult = lib.GetOcrWord(lineHandle, j, wordHandlePtr);
                if (wordResult == 0) {
                    long wordHandle = wordHandlePtr.get(JAVA_LONG, 0);
                    OcrWord word = parseOcrWord(wordHandle);
                    words.add(word);
                }
            }
        }
        
        return new OcrLine(lineText, lineBBox, words);
    }
    
    private OcrWord parseOcrWord(long wordHandle) throws Throwable {
        var tempArena = Arena.ofAuto(); // Method-local arena for temporary allocations
        
        // Get word content using output parameter
        var contentPtrPtr = tempArena.allocate(ADDRESS);
        var wordText = "";
        int contentResult = lib.GetOcrWordContent(wordHandle, contentPtrPtr);
        if (contentResult == 0) {
            var contentPtr = contentPtrPtr.get(ADDRESS, 0);
            if (!contentPtr.equals(MemorySegment.NULL)) {
                wordText = readString(contentPtr.address());
            }
        }
        
        // Get word bounding box using output parameter
        var bboxPtrPtr = tempArena.allocate(ADDRESS);
        BoundingBox wordBBox = null;
        int bboxResult = lib.GetOcrWordBoundingBox(wordHandle, bboxPtrPtr);
        if (bboxResult == 0) {
            var bboxPtr = bboxPtrPtr.get(ADDRESS, 0);
            if (!bboxPtr.equals(MemorySegment.NULL)) {
                wordBBox = readBoundingBox(bboxPtr.address());
            }
        }
        
        // Get confidence using output parameter
        var confidencePtr = tempArena.allocate(JAVA_FLOAT);
        double confidence = 0.0;
        int confidenceResult = lib.GetOcrWordConfidence(wordHandle, confidencePtr);
        if (confidenceResult == 0) {
            confidence = confidencePtr.get(JAVA_FLOAT, 0);
        }
        
        return ocrWord(wordText, wordBBox, confidence);
    }

    private BoundingBox readBoundingBox(long address) {
        if (address == 0) return null;
        
        var segment = MemorySegment.ofAddress(address).reinterpret(8 * 4); // 8 floats (4 bytes each)
        double x1 = segment.get(JAVA_FLOAT, 0);
        double y1 = segment.get(JAVA_FLOAT, 4);
        double x2 = segment.get(JAVA_FLOAT, 8);
        double y2 = segment.get(JAVA_FLOAT, 12);
        double x3 = segment.get(JAVA_FLOAT, 16);
        double y3 = segment.get(JAVA_FLOAT, 20);
        double x4 = segment.get(JAVA_FLOAT, 24);
        double y4 = segment.get(JAVA_FLOAT, 28);
        // BoundingBox coordinates should now be correct with proper x4,y4 values
        // Previous versions had a bug where x3 was repeated instead of x4,y4
        // This is now fixed and should work properly for rotated text boxes
        return new BoundingBox(x1, y1, x2, y2, x3, y3, x4, y4);
    }
    
    @Override
    public void close() {
        lib.close();
    }
 
}