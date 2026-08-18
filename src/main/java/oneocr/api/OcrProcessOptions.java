package oneocr.api;
    
public class OcrProcessOptions implements AutoCloseable {
    private final long handle;
    private final LoadNativeLib lib;

    public OcrProcessOptions(long handle, LoadNativeLib lib) {
        this.handle = handle;
        this.lib = lib;
    }

    public long getHandle() { return handle; }

    public void setMaxRecognitionLineCount(int maxLines) {
        try {
            int result = lib.OcrProcessOptionsSetMaxRecognitionLineCount(handle, maxLines);
            if (result != 0) {
                throw new RuntimeException("OcrProcessOptionsSetMaxRecognitionLineCount failed with code: " + result);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set max line count", t);
        }
    }

    public void setResizeResolution(int width, int height) {
        try {
            lib.OcrProcessOptionsSetResizeResolution(handle, width, height);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set resize resolution", t);
        }
    }

    public int getMaxRecognitionLineCount() {
        try {
            return lib.OcrProcessOptionsGetMaxRecognitionLineCount(handle);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get max line count", t);
        }
    }

    @Override
    public void close() {
        try {
            lib.ReleaseOcrProcessOptions(handle);
        } catch (Throwable ignore) {}
    }
}
