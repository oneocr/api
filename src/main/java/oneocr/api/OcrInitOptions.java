package oneocr.api;
public class OcrInitOptions implements AutoCloseable {
    private final long handle;
    private final LoadNativeLib lib;

    public OcrInitOptions(long handle, LoadNativeLib lib) {
        this.handle = handle;
        this.lib = lib;
    }

    public long getHandle() { return handle; }

    public void setUseModelDelayLoad(boolean delayLoad) {
        try {
            int result = lib.OcrInitOptionsSetUseModelDelayLoad(handle, delayLoad ? 1 : 0);
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
            lib.ReleaseOcrInitOptions(handle);
        } catch (Throwable ignore) {}
    }
}
