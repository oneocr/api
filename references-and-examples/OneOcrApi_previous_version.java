package xyz.jphil.win11_oneocr;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.*;

/**
 * Comprehensive Java FFM binding for Windows 11 SnippingTool OCR (oneocr.dll)
 * 
 * CRITICAL REFACTORING WARNING:
 * ============================ 
 * DO NOT REFACTOR THIS CLASS WITHOUT FIRST READING THE PYTHON REFERENCE IMPLEMENTATION!
 * 
 * This implementation is based on the Python ctypes binding from:
 * https://github.com/AuroraWright/oneocr
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
 * 3. Test with both JDK 21 and JDK 23 to ensure compatibility
 * 
 * Attribution:
 * Original reverse engineering and C++ implementation: https://github.com/b1tg/win11-oneocr
 * Python ctypes binding (reference for this Java port): https://github.com/AuroraWright/oneocr
 * Java FFM port with cross-JDK compatibility by Claude Code
 */
public class OneOcrApi implements AutoCloseable {
    private final Arena arena;
    private final SymbolLookup dll;
    
    // Function handles - lazy loaded
    private MethodHandle createOcrInitOptions;
    private MethodHandle createOcrPipeline; 
    private MethodHandle createOcrProcessOptions;
    private MethodHandle getImageAngle;
    private MethodHandle getOcrLine;
    private MethodHandle getOcrLineBoundingBox;
    private MethodHandle getOcrLineContent;
    private MethodHandle getOcrLineCount;
    private MethodHandle getOcrLineStyle;
    private MethodHandle getOcrLineWordCount;
    private MethodHandle getOcrWord;
    private MethodHandle getOcrWordBoundingBox;
    private MethodHandle getOcrWordConfidence;
    private MethodHandle getOcrWordContent;
    private MethodHandle ocrInitOptionsSetUseModelDelayLoad;
    private MethodHandle ocrProcessOptionsGetMaxRecognitionLineCount;
    private MethodHandle ocrProcessOptionsGetResizeResolution;
    private MethodHandle ocrProcessOptionsSetMaxRecognitionLineCount;
    private MethodHandle ocrProcessOptionsSetResizeResolution;
    private MethodHandle releaseOcrInitOptions;
    private MethodHandle releaseOcrProcessOptions;
    private MethodHandle releaseOcrPipeline;
    private MethodHandle recognizeFromImageData;
    private MethodHandle recognizeFromImageFile;
    private MethodHandle releaseOcrResult;
    
    // JDK compatibility flags and cached MethodHandles (class-level for performance)
    private static final boolean isJdk21 = isJdk21();
    private static final MethodHandle ALLOCATE_STRING_HANDLE;
    private static final MethodHandle READ_STRING_HANDLE;
    
    static {
        // Initialize MethodHandles once at class loading time for optimal performance
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        
        if (isJdk21) {
            try {
                // JDK21 - allocateUtf8String(String) - no charset parameter needed
                ALLOCATE_STRING_HANDLE = lookup.findVirtual(Arena.class, "allocateUtf8String", 
                    MethodType.methodType(MemorySegment.class, String.class));
                READ_STRING_HANDLE = lookup.findVirtual(MemorySegment.class, "getUtf8String", 
                    MethodType.methodType(String.class, long.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("JDK21 FFM methods not found - incompatible JDK21 version", e);
            }
        } else {
            try {
                // JDK22+ - Try charset version first, fallback to simple version
                MethodHandle allocateHandle = null;
                try {
                    // Try the charset version first (more explicit)
                    allocateHandle = lookup.findVirtual(Arena.class, "allocateFrom", 
                        MethodType.methodType(MemorySegment.class, String.class, java.nio.charset.Charset.class));
                } catch (NoSuchMethodException e1) {
                    // Fallback to non-charset version
                    allocateHandle = lookup.findVirtual(Arena.class, "allocateFrom", 
                        MethodType.methodType(MemorySegment.class, String.class));
                }
                ALLOCATE_STRING_HANDLE = allocateHandle;
                
                READ_STRING_HANDLE = lookup.findVirtual(MemorySegment.class, "getString", 
                    MethodType.methodType(String.class, long.class, java.nio.charset.Charset.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("JDK22+ FFM methods not found", e);
            }
        }
    }
    
    public OneOcrApi() throws IOException {
        this.arena = Arena.ofConfined();
        this.dll = loadDllWithDependencies();
        initializeFunctionHandles();
    }
    
    private static boolean isJdk21() {
        String version = System.getProperty("java.version");
        return version.startsWith("21.");
    }
    
    private SymbolLookup loadDllWithDependencies() throws IOException {
        // Use robust extraction pattern similar to JavaFX/LWJGL
        Path nativesDir = ensureNativesExtracted();
        
        // Load dependencies in correct order
        String[] dependencies = {"onnxruntime.dll", "opencv_world4120.dll"};
        for (String dep : dependencies) {
            Path depPath = nativesDir.resolve(dep);
            if (Files.exists(depPath)) {
                System.load(depPath.toAbsolutePath().toString());
            }
        }
        
        // Load main OCR DLL
        Path ocrDllPath = nativesDir.resolve("oneocr.dll");
        if (!Files.exists(ocrDllPath)) {
            throw new IOException("oneocr.dll not found after extraction: " + ocrDllPath);
        }
        
        return SymbolLookup.libraryLookup(ocrDllPath, arena);
    }
    
    /**
     * Ensures native libraries are extracted to user home directory.
     * Uses pattern: <userhome>/xyz-jphil/win11_oneocr/
     * 
     * Logic:
     * 1. Check if DLLs exist in extraction directory
     * 2. If not, extract from JAR resources or copy from development classpath
     * 3. Return path to extraction directory
     */
    private Path ensureNativesExtracted() throws IOException {
        // Target extraction directory
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path extractDir = userHome.resolve("xyz-jphil").resolve("win11_oneocr");
        
        // Create directory if it doesn't exist
        if (!Files.exists(extractDir)) {
            Files.createDirectories(extractDir);
        }
        
        // Native library files to extract
        String[] nativeFiles = {
            "oneocr.dll",
            "oneocr.onemodel", 
            "onnxruntime.dll",
            "opencv_world4120.dll"
        };
        
        // Check if extraction is needed
        boolean extractionNeeded = false;
        for (String fileName : nativeFiles) {
            Path targetFile = extractDir.resolve(fileName);
            if (!Files.exists(targetFile)) {
                extractionNeeded = true;
                break;
            }
        }
        
        if (extractionNeeded) {
            System.out.println("Extracting native libraries to: " + extractDir);
            
            for (String fileName : nativeFiles) {
                Path targetFile = extractDir.resolve(fileName);
                
                // Try to extract from JAR resources first
                try (var resourceStream = getClass().getResourceAsStream("/natives/" + fileName)) {
                    if (resourceStream != null) {
                        Files.copy(resourceStream, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Extracted from JAR: " + fileName);
                    } else {
                        // Fallback: try to find in development classpath
                        if (extractFromDevelopmentClasspath(fileName, targetFile)) {
                            System.out.println("Copied from development classpath: " + fileName);
                        } else {
                            // Optional files (like opencv) might not exist
                            if (fileName.equals("oneocr.dll") || fileName.equals("oneocr.onemodel")) {
                                throw new IOException("Required native file not found: " + fileName);
                            }
                        }
                    }
                } catch (IOException e) {
                    if (fileName.equals("oneocr.dll") || fileName.equals("oneocr.onemodel")) {
                        throw new IOException("Failed to extract required native file: " + fileName, e);
                    }
                    // Optional files - continue
                }
            }
        }
        
        return extractDir;
    }
    
    /**
     * Fallback extraction for development environment (running from Maven/IDE)
     */
    private boolean extractFromDevelopmentClasspath(String fileName, Path targetFile) throws IOException {
        // Search common development paths
        Path currentDir = Paths.get("").toAbsolutePath();
        Path[] searchPaths = {
            currentDir.resolve("nativelibs"), // Original location
            currentDir.resolve("target"), // Maven target
            currentDir.resolve("src/main/resources/natives"), // New location
            currentDir.resolve(".."), // Parent directory
        };
        
        for (Path searchPath : searchPaths) {
            Path sourceFile = searchPath.resolve(fileName);
            if (Files.exists(sourceFile)) {
                Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        }
        
        return false;
    }
    
    private Path findModelFile() {
        // Model file is now managed by ensureNativesExtracted()
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path modelFile = userHome.resolve("xyz-jphil").resolve("win11_oneocr").resolve("oneocr.onemodel");
        
        if (Files.exists(modelFile)) {
            return modelFile;
        }
        
        return null;
    }
    
    private void initializeFunctionHandles() {
        // Python reference (oneocr_reference.py) - DLL_FUNCTIONS list:
        // All function signatures MUST match these ctypes declarations exactly:
        // ('CreateOcrInitOptions', [c_int64_p], c_int64),
        // ('CreateOcrPipeline', [c_char_p, c_char_p, c_int64, c_int64_p], c_int64),
        // ('CreateOcrProcessOptions', [c_int64_p], c_int64),
        // ('RunOcrPipeline', [c_int64, POINTER(ImageStructure), c_int64, c_int64_p], c_int64),
        // ('GetOcrLineCount', [c_int64, c_int64_p], c_int64),
        // ('GetOcrLine', [c_int64, c_int64, c_int64_p], c_int64),
        // etc... - see full list in oneocr_reference.py
        
        try {
            // Core pipeline functions
            // CreateOcrInitOptions(init_options_ptr) -> int
            createOcrInitOptions = getFunction("CreateOcrInitOptions", 
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
            // CreateOcrPipeline(model_path, model_key, init_options, pipeline_ptr) -> int  
            createOcrPipeline = getFunction("CreateOcrPipeline", 
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
            // CreateOcrProcessOptions(process_options_ptr) -> int
            createOcrProcessOptions = getFunction("CreateOcrProcessOptions", 
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
                
            // Image processing
            // GetImageAngle(ocr_result, angle_ptr) -> int
            getImageAngle = getFunction("GetImageAngle", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
                
            // Line-level functions  
            // GetOcrLineCount(ocr_result, line_count_ptr) -> int
            getOcrLineCount = getFunction("GetOcrLineCount", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            // GetOcrLine(ocr_result, line_index, line_handle_ptr) -> int
            getOcrLine = getFunction("GetOcrLine", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS));
            // GetOcrLineBoundingBox(line_handle, bbox_ptr) -> int
            getOcrLineBoundingBox = getFunction("GetOcrLineBoundingBox", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            // GetOcrLineContent(line_handle, content_ptr) -> int
            getOcrLineContent = getFunction("GetOcrLineContent", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            getOcrLineStyle = getFunction("GetOcrLineStyle", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
            // GetOcrLineWordCount(line_handle, word_count_ptr) -> int
            getOcrLineWordCount = getFunction("GetOcrLineWordCount", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
                
            // Word-level functions
            // GetOcrWord(line_handle, word_index, word_handle_ptr) -> int
            getOcrWord = getFunction("GetOcrWord", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS));
            // GetOcrWordBoundingBox(word_handle, bbox_ptr) -> int
            getOcrWordBoundingBox = getFunction("GetOcrWordBoundingBox", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            // GetOcrWordConfidence(word_handle, confidence_ptr) -> int
            getOcrWordConfidence = getFunction("GetOcrWordConfidence", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
            // GetOcrWordContent(word_handle, content_ptr) -> int
            getOcrWordContent = getFunction("GetOcrWordContent", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS));
                
            // Configuration functions
            // OcrInitOptionsSetUseModelDelayLoad(init_options, delay_load) -> int
            ocrInitOptionsSetUseModelDelayLoad = getFunction("OcrInitOptionsSetUseModelDelayLoad", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
            // OcrProcessOptionsSetMaxRecognitionLineCount(process_options, max_lines) -> int
            ocrProcessOptionsSetMaxRecognitionLineCount = getFunction("OcrProcessOptionsSetMaxRecognitionLineCount", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
            ocrProcessOptionsSetResizeResolution = getFunction("OcrProcessOptionsSetResizeResolution", 
                FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT));
            ocrProcessOptionsGetMaxRecognitionLineCount = getFunction("OcrProcessOptionsGetMaxRecognitionLineCount", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
            ocrProcessOptionsGetResizeResolution = getFunction("OcrProcessOptionsGetResizeResolution", 
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG)); // Returns pair of ints
                
            // Cleanup functions  
            releaseOcrInitOptions = getFunction("ReleaseOcrInitOptions", 
                FunctionDescriptor.ofVoid(JAVA_LONG));
            releaseOcrProcessOptions = getFunction("ReleaseOcrProcessOptions", 
                FunctionDescriptor.ofVoid(JAVA_LONG));
            releaseOcrPipeline = getFunction("ReleaseOcrPipeline", 
                FunctionDescriptor.ofVoid(JAVA_LONG));
            releaseOcrResult = getFunction("ReleaseOcrResult", 
                FunctionDescriptor.ofVoid(JAVA_LONG));
                
            // Core pipeline execution (the main OCR function that actually exists)
            // RunOcrPipeline(pipeline, image_struct_ptr, process_options, result_ptr) -> int
            recognizeFromImageData = getFunction("RunOcrPipeline", 
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS));
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OCR function handles", e);
        }
    }
    
    private MethodHandle getFunction(String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = dll.find(name);
        if (symbol.isEmpty()) {
            throw new RuntimeException("Function not found in oneocr.dll: " + name);
        }
        return Linker.nativeLinker().downcallHandle(symbol.get(), descriptor);
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
        
        try {
            MemorySegment handlePtr = arena.allocate(JAVA_LONG);
            int result = (int) createOcrInitOptions.invoke(handlePtr);
            if (result != 0) {
                throw new RuntimeException("CreateOcrInitOptions failed with code: " + result);
            }
            long handle = handlePtr.get(JAVA_LONG, 0);
            
            // Set model delay load to false (0) like Python code
            int delayResult = (int) ocrInitOptionsSetUseModelDelayLoad.invoke(handle, 0);
            if (delayResult != 0) {
                throw new RuntimeException("OcrInitOptionsSetUseModelDelayLoad failed with code: " + delayResult);
            }
            
            return new OcrInitOptions(handle, this);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create OCR init options", t);
        }
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
        
        try {
            // Find model file - look for oneocr.onemodel
            Path modelPath = findModelFile();
            if (modelPath == null) {
                throw new RuntimeException("Could not find oneocr.onemodel file");
            }
            
            MemorySegment modelPathStr = allocateString(modelPath.toString());
            MemorySegment modelKey = allocateString("kj)TGtrK>f]b[Piow.gU+nC@s\"\"\"\"\"\"4"); // From Python code
            MemorySegment pipelinePtr = arena.allocate(JAVA_LONG);
            
            int result = (int) createOcrPipeline.invoke(modelPathStr, modelKey, initOptions.getHandle(), pipelinePtr);
            if (result != 0) {
                throw new RuntimeException("CreateOcrPipeline failed with code: " + result);
            }
            
            long handle = pipelinePtr.get(JAVA_LONG, 0);
            return new OcrPipeline(handle, this);
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
        
        try {
            MemorySegment processOptionsPtr = arena.allocate(JAVA_LONG);
            int result = (int) createOcrProcessOptions.invoke(processOptionsPtr);
            if (result != 0) {
                throw new RuntimeException("CreateOcrProcessOptions failed with code: " + result);
            }
            long handle = processOptionsPtr.get(JAVA_LONG, 0);
            
            // Set max recognition line count (default 1000 matches all reference implementations)
            int lineCountResult = (int) ocrProcessOptionsSetMaxRecognitionLineCount.invoke(handle, maxRecognitionLineCount);
            if (lineCountResult != 0) {
                throw new RuntimeException("OcrProcessOptionsSetMaxRecognitionLineCount failed with code: " + lineCountResult);
            }
            
            return new OcrProcessOptions(handle, this);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to create OCR process options", t);
        }
    }
    
    public OcrResult recognizeFromFile(OcrPipeline pipeline, OcrProcessOptions options, String imagePath) {
        try {
            MemorySegment pathSegment = allocateString(imagePath);
            long resultHandle = (long) recognizeFromImageFile.invoke(
                pipeline.getHandle(), options.getHandle(), pathSegment.address());
            return parseOcrResult(resultHandle);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to recognize from file: " + imagePath, t);
        }
    }
    
    /**
     * Perform OCR on BGRA image data - cross-JDK compatible version
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
        
        try {
            // Create image structure - match C++ Img struct exactly (32 bytes)
            MemorySegment imageStruct = arena.allocate(32);
            
            // Allocate image data - stable across JDK versions (TODO: optimize to avoid copy)
            MemorySegment imageDataPtr = arena.allocate(imageData.length);
            MemorySegment.copy(MemorySegment.ofArray(imageData), 0, imageDataPtr, 0, imageData.length);
            
            // Fill image structure
            imageStruct.set(JAVA_INT, 0, 3);                    // CV_8UC4
            imageStruct.set(JAVA_INT, 4, width);               // width
            imageStruct.set(JAVA_INT, 8, height);              // height  
            imageStruct.set(JAVA_INT, 12, 0);                  // reserved
            imageStruct.set(JAVA_LONG, 16, width * 4L);        // step
            imageStruct.set(JAVA_LONG, 24, imageDataPtr.address()); // data_ptr as long
            
            // Run OCR - RunOcrPipeline(pipeline, image_struct_ptr, process_options, result_ptr) -> int
            MemorySegment resultPtr = arena.allocate(JAVA_LONG);
            int result = (int) recognizeFromImageData.invoke(
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
        
        try {
            // Get line count using output parameter
            MemorySegment lineCountPtr = arena.allocate(JAVA_LONG);
            int result = (int) getOcrLineCount.invoke(resultHandle, lineCountPtr);
            if (result != 0) {
                return new OcrResult("", 0.0f, List.of());
            }
            int lineCount = (int) lineCountPtr.get(JAVA_LONG, 0);
            List<OcrLine> lines = new ArrayList<>();
            
            // Get text angle using output parameter
            double textAngle = 0.0;
            try {
                MemorySegment anglePtr = arena.allocate(JAVA_FLOAT);
                int angleResult = (int) getImageAngle.invoke(resultHandle, anglePtr);
                if (angleResult == 0) {
                    textAngle = anglePtr.get(JAVA_FLOAT, 0);
                }
            } catch (Throwable ignore) {
                // Angle might not be available for all result types
            }
            
            for (int i = 0; i < lineCount; i++) {
                MemorySegment lineHandlePtr = arena.allocate(JAVA_LONG);
                int lineResult = (int) getOcrLine.invoke(resultHandle, i, lineHandlePtr);
                if (lineResult == 0) {
                    long lineHandle = lineHandlePtr.get(JAVA_LONG, 0);
                    OcrLine line = parseOcrLine(lineHandle);
                    lines.add(line);
                }
            }
            
            // Combine all line texts
            String fullText = lines.stream()
                    .map(OcrLine::text)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            
            return new OcrResult(fullText, textAngle, lines);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to parse OCR result", t);
        } finally {
            try {
                releaseOcrResult.invoke(resultHandle);
            } catch (Throwable ignore) {}
        }
    }
    
    private OcrLine parseOcrLine(long lineHandle) throws Throwable {
        // Get line content using output parameter
        MemorySegment contentPtrPtr = arena.allocate(ADDRESS);
        String lineText = "";
        int contentResult = (int) getOcrLineContent.invoke(lineHandle, contentPtrPtr);
        if (contentResult == 0) {
            MemorySegment contentPtr = contentPtrPtr.get(ADDRESS, 0);
            if (!contentPtr.equals(MemorySegment.NULL)) {
                lineText = readString(contentPtr.address());
            }
        }
        
        // Get line bounding box using output parameter  
        MemorySegment bboxPtrPtr = arena.allocate(ADDRESS);
        BoundingBox lineBBox = null;
        int bboxResult = (int) getOcrLineBoundingBox.invoke(lineHandle, bboxPtrPtr);
        if (bboxResult == 0) {
            MemorySegment bboxPtr = bboxPtrPtr.get(ADDRESS, 0);
            if (!bboxPtr.equals(MemorySegment.NULL)) {
                lineBBox = readBoundingBox(bboxPtr.address());
            }
        }
        
        // Get words in line using output parameter
        MemorySegment wordCountPtr = arena.allocate(JAVA_LONG);
        int wordCountResult = (int) getOcrLineWordCount.invoke(lineHandle, wordCountPtr);
        List<OcrWord> words = new ArrayList<>();
        
        if (wordCountResult == 0) {
            int wordCount = (int) wordCountPtr.get(JAVA_LONG, 0);
            
            for (int j = 0; j < wordCount; j++) {
                MemorySegment wordHandlePtr = arena.allocate(JAVA_LONG);
                int wordResult = (int) getOcrWord.invoke(lineHandle, j, wordHandlePtr);
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
        // Get word content using output parameter
        MemorySegment contentPtrPtr = arena.allocate(ADDRESS);
        String wordText = "";
        int contentResult = (int) getOcrWordContent.invoke(wordHandle, contentPtrPtr);
        if (contentResult == 0) {
            MemorySegment contentPtr = contentPtrPtr.get(ADDRESS, 0);
            if (!contentPtr.equals(MemorySegment.NULL)) {
                wordText = readString(contentPtr.address());
            }
        }
        
        // Get word bounding box using output parameter
        MemorySegment bboxPtrPtr = arena.allocate(ADDRESS);
        BoundingBox wordBBox = null;
        int bboxResult = (int) getOcrWordBoundingBox.invoke(wordHandle, bboxPtrPtr);
        if (bboxResult == 0) {
            MemorySegment bboxPtr = bboxPtrPtr.get(ADDRESS, 0);
            if (!bboxPtr.equals(MemorySegment.NULL)) {
                wordBBox = readBoundingBox(bboxPtr.address());
            }
        }
        
        // Get confidence using output parameter
        MemorySegment confidencePtr = arena.allocate(JAVA_FLOAT);
        double confidence = 0.0;
        int confidenceResult = (int) getOcrWordConfidence.invoke(wordHandle, confidencePtr);
        if (confidenceResult == 0) {
            confidence = confidencePtr.get(JAVA_FLOAT, 0);
        }
        
        return new OcrWord(wordText, wordBBox, confidence);
    }
    
    // JDK21/22+ compatibility methods using cached MethodHandles for performance
    private MemorySegment allocateString(String text) {
        try {
            if (isJdk21) {
                // JDK21: allocateUtf8String(String) - only text parameter
                return (MemorySegment) ALLOCATE_STRING_HANDLE.invoke(arena, text);
            } else {
                // JDK22+: May need charset parameter depending on which method was found
                try {
                    // Try with charset first (if that's what was found)
                    return (MemorySegment) ALLOCATE_STRING_HANDLE.invoke(arena, text, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Throwable e1) {
                    // Fallback to no charset version
                    return (MemorySegment) ALLOCATE_STRING_HANDLE.invoke(arena, text);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to allocate string using cached MethodHandle", t);
        }
    }
    
    private String readString(long address) {
        if (address == 0) return "";
        
        MemorySegment segment = MemorySegment.ofAddress(address).reinterpret(Long.MAX_VALUE);
        
        try {
            if (isJdk21) {
                // JDK21: getUtf8String(long) - no charset parameter
                return (String) READ_STRING_HANDLE.invoke(segment, 0L);
            } else {
                // JDK22+: getString(long, Charset) - requires explicit charset
                return (String) READ_STRING_HANDLE.invoke(segment, 0L, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to read string using cached MethodHandle", t);
        }
    }
    
    private BoundingBox readBoundingBox(long address) {
        if (address == 0) return null;
        
        MemorySegment segment = MemorySegment.ofAddress(address).reinterpret(8 * 4); // 8 floats (4 bytes each)
        double x1 = segment.get(JAVA_FLOAT, 0);
        double y1 = segment.get(JAVA_FLOAT, 4);
        double x2 = segment.get(JAVA_FLOAT, 8);
        double y2 = segment.get(JAVA_FLOAT, 12);
        double x3 = segment.get(JAVA_FLOAT, 16);
        double y3 = segment.get(JAVA_FLOAT, 20);
        double x4 = segment.get(JAVA_FLOAT, 24);
        double y4 = segment.get(JAVA_FLOAT, 28);
        
        return new BoundingBox(x1, y1, x2, y2, x3, y3, x4, y4);
    }
    
    @Override
    public void close() {
        arena.close();
    }
    
    // Nested classes for wrapped handles
    public static class OcrInitOptions implements AutoCloseable {
        private final long handle;
        private final OneOcrApi api;
        
        OcrInitOptions(long handle, OneOcrApi api) {
            this.handle = handle;
            this.api = api;
        }
        
        public long getHandle() { return handle; }
        
        public void setUseModelDelayLoad(boolean delayLoad) {
            try {
                int result = (int) api.ocrInitOptionsSetUseModelDelayLoad.invoke(handle, delayLoad ? 1 : 0);
                if (result != 0) {
                    throw new RuntimeException("OcrInitOptionsSetUseModelDelayLoad failed with code: " + result);
                }
            } catch (Throwable t) {
                throw new RuntimeException("Failed to set model delay load", t);
            }
        }
        
        @Override
        public void close() {
            try {
                api.releaseOcrInitOptions.invoke(handle);
            } catch (Throwable ignore) {}
        }
    }
    
    public static class OcrPipeline implements AutoCloseable {
        private final long handle;
        private final OneOcrApi api;
        
        OcrPipeline(long handle, OneOcrApi api) {
            this.handle = handle;
            this.api = api;
        }
        
        public long getHandle() { return handle; }
        
        @Override
        public void close() {
            try {
                api.releaseOcrPipeline.invoke(handle);
            } catch (Throwable ignore) {}
        }
    }
    
    public static class OcrProcessOptions implements AutoCloseable {
        private final long handle;
        private final OneOcrApi api;
        
        OcrProcessOptions(long handle, OneOcrApi api) {
            this.handle = handle;
            this.api = api;
        }
        
        public long getHandle() { return handle; }
        
        public void setMaxRecognitionLineCount(int maxLines) {
            try {
                int result = (int) api.ocrProcessOptionsSetMaxRecognitionLineCount.invoke(handle, maxLines);
                if (result != 0) {
                    throw new RuntimeException("OcrProcessOptionsSetMaxRecognitionLineCount failed with code: " + result);
                }
            } catch (Throwable t) {
                throw new RuntimeException("Failed to set max line count", t);
            }
        }
        
        public void setResizeResolution(int width, int height) {
            try {
                api.ocrProcessOptionsSetResizeResolution.invoke(handle, width, height);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to set resize resolution", t);
            }
        }
        
        public int getMaxRecognitionLineCount() {
            try {
                return (int) api.ocrProcessOptionsGetMaxRecognitionLineCount.invoke(handle);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to get max line count", t);
            }
        }
        
        @Override
        public void close() {
            try {
                api.releaseOcrProcessOptions.invoke(handle);
            } catch (Throwable ignore) {}
        }
    }
}