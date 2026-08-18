package xyz.jphil.win11_oneocr;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import static xyz.jphil.win11_oneocr.OneOcrHomeDir.get;

public class LoadNativeLib implements AutoCloseable {
    private final SymbolLookup dll;
    private final Arena arena;
    
    private MethodHandle createOcrInitOptions,createOcrPipeline,createOcrProcessOptions,getImageAngle,getOcrLine,getOcrLineBoundingBox,getOcrLineContent,getOcrLineCount,getOcrLineStyle,getOcrLineWordCount,getOcrWord,getOcrWordBoundingBox,getOcrWordConfidence,getOcrWordContent,ocrInitOptionsSetUseModelDelayLoad,ocrProcessOptionsGetMaxRecognitionLineCount,ocrProcessOptionsGetResizeResolution,ocrProcessOptionsSetMaxRecognitionLineCount,ocrProcessOptionsSetResizeResolution,releaseOcrInitOptions,releaseOcrProcessOptions,releaseOcrPipeline,runOcrPipeline,releaseOcrResult;
    
    private final boolean closableArena;
    
    LoadNativeLib() throws IOException {
        //this(Arena.ofConfined(),true);
        this(Arena.ofAuto(),false);
        // `Arena.ofConfined();`
        // will not work for us as we are
        // not cleaning any resources!
        // So we need GC to do this, 
        // which is when we use ofAuto
        // we don't know if it will be thread safe
        // so better we should not leak these references
    }
    
    LoadNativeLib(Arena arena, boolean closableArena) throws IOException {
        this.arena = arena;
        this.closableArena = closableArena;
        this.dll = loadDllWithDependencies(arena);
    }

    // let each method make their own
    // confined or auto arena and carefully
    // handle clearing their allocations
    Arena arena() {
        return arena;
    }
    
    @Override
    public void close() {
        if(closableArena)arena.close();
    }

    static SymbolLookup loadDllWithDependencies(Arena arena) throws IOException {
        // Use robust extraction pattern similar to JavaFX/LWJGL
        var nativesDir = ensureNativesExtracted();
        
        // Load dependencies in correct order
        String[] dependencies = {"onnxruntime.dll", "opencv_world4120.dll"};
        for (var dep : dependencies) {
            var depPath = nativesDir.resolve(dep);
            if (Files.exists(depPath)) {
                System.load(depPath.toAbsolutePath().toString());
            }
        }
        
        // Load main OCR DLL
        var ocrDllPath = nativesDir.resolve("oneocr.dll");
        if (!Files.exists(ocrDllPath)) {
            throw new IOException("oneocr.dll not found after extraction: " + ocrDllPath);
        }
        
        return SymbolLookup.libraryLookup(ocrDllPath, arena);
    }
    
    MethodHandle funcHndl(String name, MemoryLayout resLayout, MemoryLayout... argLayouts) {
        Optional<MemorySegment> symbol = dll.find(name);
        if (symbol.isEmpty()) {
            throw new RuntimeException("Function not found in oneocr.dll: " + name);
        }
        var descriptor = FunctionDescriptor.of(resLayout, argLayouts);
        return Linker.nativeLinker().downcallHandle(symbol.get(), descriptor);
    }
    
    MethodHandle voidFuncHndl(String name, MemoryLayout... argLayouts) {
        Optional<MemorySegment> symbol = dll.find(name);
        if (symbol.isEmpty()) {
            throw new RuntimeException("Function not found in oneocr.dll: " + name);
        }
        var descriptor = FunctionDescriptor.ofVoid(argLayouts);
        return Linker.nativeLinker().downcallHandle(symbol.get(), descriptor);
    }
    
    /**
     * Ensures native libraries are extracted to app home directory.
     * Logic:
     * 1. Check if DLLs exist in extraction directory
     * 2. If not, extract from JAR resources or copy from development classpath
     * 3. Return path to extraction directory
     */
    private static Path ensureNativesExtracted() throws IOException {
        // Target extraction directory
        var extractDir = get(true);
        
        // Native library files to extract
        String[] nativeFiles = {
            "oneocr.dll",
            "oneocr.onemodel", 
            "onnxruntime.dll",
            "opencv_world4120.dll"
        };
        
        // Check if extraction is needed
        boolean extractionNeeded = false;
        for (var fileName : nativeFiles) {
            var targetFile = extractDir.resolve(fileName);
            if (!Files.exists(targetFile)) {
                extractionNeeded = true;
                break;
            }
        }
        
        if (extractionNeeded) {
            System.out.println("Extracting native libraries to: " + extractDir);
            
            for (var fileName : nativeFiles) {
                var targetFile = extractDir.resolve(fileName);
                
                // Try to extract from JAR resources first
                try (var resourceStream = 
                        LoadNativeLib.class.getResourceAsStream("/natives/" + fileName)) {
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
    private static boolean extractFromDevelopmentClasspath(String fileName, Path targetFile) throws IOException {
        // Search common development paths
        var currentDir = Paths.get("").toAbsolutePath();
        Path[] searchPaths = {
            currentDir.resolve("nativelibs"), // Original location
            currentDir.resolve("target"), // Maven target
            currentDir.resolve("src/main/resources/natives"), // New location
            currentDir.resolve(".."), // Parent directory
        };
        
        for (var searchPath : searchPaths) {
            var sourceFile = searchPath.resolve(fileName);
            if (Files.exists(sourceFile)) {
                Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        }
        
        return false;
    }
    
    
    void initializeFunctionHandles() {
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
            createOcrInitOptions = funcHndl(
                "CreateOcrInitOptions", JAVA_INT, ADDRESS);
            // CreateOcrPipeline(model_path, model_key, init_options, pipeline_ptr) -> int  
            createOcrPipeline = funcHndl(
                "CreateOcrPipeline", JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS);
            // CreateOcrProcessOptions(process_options_ptr) -> int
            createOcrProcessOptions = funcHndl(
                "CreateOcrProcessOptions", JAVA_INT, ADDRESS);
                
            // Image processing
            // GetImageAngle(ocr_result, angle_ptr) -> int
            getImageAngle = funcHndl(
                "GetImageAngle", JAVA_INT, JAVA_LONG, ADDRESS);
                
            // Line-level functions  
            // GetOcrLineCount(ocr_result, line_count_ptr) -> int
            getOcrLineCount = funcHndl(
                "GetOcrLineCount", JAVA_INT, JAVA_LONG, ADDRESS);
            // GetOcrLine(ocr_result, line_index, line_handle_ptr) -> int
            getOcrLine = funcHndl(
                "GetOcrLine", JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS);
            // GetOcrLineBoundingBox(line_handle, bbox_ptr) -> int
            getOcrLineBoundingBox = funcHndl(
                "GetOcrLineBoundingBox", JAVA_INT, JAVA_LONG, ADDRESS);
            // GetOcrLineContent(line_handle, content_ptr) -> int
            getOcrLineContent = funcHndl(
                "GetOcrLineContent", JAVA_INT, JAVA_LONG, ADDRESS);
            getOcrLineStyle = funcHndl(
                "GetOcrLineStyle", JAVA_INT, JAVA_LONG);
            // GetOcrLineWordCount(line_handle, word_count_ptr) -> int
            getOcrLineWordCount = funcHndl(
                "GetOcrLineWordCount", JAVA_INT, JAVA_LONG, ADDRESS);
                
            // Word-level functions
            // GetOcrWord(line_handle, word_index, word_handle_ptr) -> int
            getOcrWord = funcHndl(
                "GetOcrWord", JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS);
            // GetOcrWordBoundingBox(word_handle, bbox_ptr) -> int
            getOcrWordBoundingBox = funcHndl(
                "GetOcrWordBoundingBox", JAVA_INT, JAVA_LONG, ADDRESS);
            // GetOcrWordConfidence(word_handle, confidence_ptr) -> int
            getOcrWordConfidence = funcHndl(
                "GetOcrWordConfidence", JAVA_INT, JAVA_LONG, ADDRESS);
            // GetOcrWordContent(word_handle, content_ptr) -> int
            getOcrWordContent = funcHndl(
                "GetOcrWordContent", JAVA_INT, JAVA_LONG, ADDRESS);
                
            // Configuration functions
            // OcrInitOptionsSetUseModelDelayLoad(init_options, delay_load) -> int
            ocrInitOptionsSetUseModelDelayLoad = funcHndl(
                "OcrInitOptionsSetUseModelDelayLoad", JAVA_INT, JAVA_LONG, JAVA_INT);
            // OcrProcessOptionsSetMaxRecognitionLineCount(process_options, max_lines) -> int
            ocrProcessOptionsSetMaxRecognitionLineCount = funcHndl(
                "OcrProcessOptionsSetMaxRecognitionLineCount", 
                JAVA_INT, JAVA_LONG, JAVA_INT);
            ocrProcessOptionsSetResizeResolution = voidFuncHndl(
                "OcrProcessOptionsSetResizeResolution", 
                JAVA_LONG, JAVA_INT, JAVA_INT);
            ocrProcessOptionsGetMaxRecognitionLineCount = funcHndl(
                "OcrProcessOptionsGetMaxRecognitionLineCount", 
                JAVA_INT, JAVA_LONG);
            ocrProcessOptionsGetResizeResolution = funcHndl(
                "OcrProcessOptionsGetResizeResolution", JAVA_LONG, JAVA_LONG); // Returns ints pair
                
            // Cleanup functions - these return void, not long
            releaseOcrInitOptions = voidFuncHndl("ReleaseOcrInitOptions", JAVA_LONG);
            releaseOcrProcessOptions = voidFuncHndl("ReleaseOcrProcessOptions", JAVA_LONG);
            releaseOcrPipeline = voidFuncHndl("ReleaseOcrPipeline", JAVA_LONG);
            releaseOcrResult = voidFuncHndl("ReleaseOcrResult", JAVA_LONG);
                
            // Core pipeline execution (the main OCR function that actually exists)
            // RunOcrPipeline(pipeline, image_struct_ptr, process_options, result_ptr) -> int
            runOcrPipeline = funcHndl(
                "RunOcrPipeline", JAVA_INT, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS);
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OCR function handles", e);
        }
    }

    int CreateOcrInitOptions(MemorySegment p1) throws Throwable {
        return (int)createOcrInitOptions.invoke(p1);
    }

    int CreateOcrPipeline(MemorySegment modelPath, MemorySegment modelKey, long initOptionsHandle, MemorySegment pipelinePtr) throws Throwable {
        return (int)createOcrPipeline.invoke(modelPath,modelKey,initOptionsHandle,pipelinePtr);
    }

    int CreateOcrProcessOptions(MemorySegment processOptionsPtr) throws Throwable {
        return (int) createOcrProcessOptions.invoke(processOptionsPtr);
    }

    int GetImageAngle(long resultHandle, MemorySegment anglePtr) throws Throwable {
        return (int) getImageAngle.invoke(resultHandle, anglePtr);
    }

    int GetOcrLine(long resultHandle, int i, MemorySegment lineHandlePtr) throws Throwable {
        return (int) getOcrLine.invoke(resultHandle, i, lineHandlePtr);
    }

    int GetOcrLineBoundingBox(long lineHandle, MemorySegment bboxPtrPtr) throws Throwable {
        return (int) getOcrLineBoundingBox.invoke(lineHandle, bboxPtrPtr);
    }

    int GetOcrLineContent(long lineHandle, MemorySegment contentPtrPtr) throws Throwable {
        return (int)getOcrLineContent.invoke(lineHandle, contentPtrPtr);
    }

    int GetOcrLineCount(long resultHandle, MemorySegment lineCountPtr) throws Throwable {
        return (int)getOcrLineCount.invoke(resultHandle,lineCountPtr);
    }

    Object GetOcrLineStyle(int p1, long p2) throws Throwable {
        return getOcrLineStyle.invoke(p1,p2);
    }

    int GetOcrLineWordCount(long lineHandle, MemorySegment wordCountPtr) throws Throwable {
        return (int)getOcrLineWordCount.invoke(lineHandle, wordCountPtr);
    }

    int GetOcrWord(long lineHandle, int j, MemorySegment wordHandlePtr) throws Throwable {
        return (int)getOcrWord.invoke(lineHandle,j,wordHandlePtr);
    }

    int GetOcrWordBoundingBox(long wordHandle, MemorySegment bboxPtrPtr) throws Throwable {
        return (int) getOcrWordBoundingBox.invoke(wordHandle, bboxPtrPtr);
    }

    int GetOcrWordConfidence(long wordHandle, MemorySegment confidencePtr) throws Throwable {
        return (int) getOcrWordConfidence.invoke(wordHandle, confidencePtr);
    }

    int GetOcrWordContent(long wordHandle, MemorySegment contentPtrPtr) throws Throwable {
        return (int)getOcrWordContent.invoke(wordHandle, contentPtrPtr);
    }

    int OcrInitOptionsSetUseModelDelayLoad(long handle, int delayLoad) throws Throwable {
        return (int)ocrInitOptionsSetUseModelDelayLoad.invoke(handle, delayLoad);
    }

    int OcrProcessOptionsGetMaxRecognitionLineCount(long handle) throws Throwable {
        return (int) ocrProcessOptionsGetMaxRecognitionLineCount.invoke(handle);
    }

    Object OcrProcessOptionsGetResizeResolution(long p1, long p2) throws Throwable {
        return ocrProcessOptionsGetResizeResolution.invoke(p1,p2);// Returns ints pair
    }

    int OcrProcessOptionsSetMaxRecognitionLineCount(long handle, int maxRecognitionLineCount) throws Throwable {
        return (int) ocrProcessOptionsSetMaxRecognitionLineCount.invoke(handle,maxRecognitionLineCount);
    }

    Object OcrProcessOptionsSetResizeResolution(long p1, int p2, int p3) throws Throwable {
        return ocrProcessOptionsSetResizeResolution.invoke(p1,p2,p3);
    }
    
    
    int RunOcrPipeline(long pipelineHandle, MemorySegment imageStruct, long optionsHandle, MemorySegment resultPtr) throws Throwable {
        return (int) runOcrPipeline.invoke(pipelineHandle, imageStruct, optionsHandle, resultPtr);
    }

    void ReleaseOcrInitOptions(long handle) throws Throwable {
        releaseOcrInitOptions.invoke(handle);
    }

    void ReleaseOcrProcessOptions(long handle) throws Throwable {
        releaseOcrProcessOptions.invoke(handle);
    }

    void ReleaseOcrPipeline(long handle) throws Throwable {
        releaseOcrPipeline.invoke(handle);
    }

    void ReleaseOcrResult(long resultHandle) throws Throwable {
        releaseOcrResult.invoke(resultHandle);
    }
    
    
    
}
