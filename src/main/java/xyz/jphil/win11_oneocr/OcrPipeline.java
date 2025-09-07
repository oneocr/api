package xyz.jphil.win11_oneocr;
public class OcrPipeline implements AutoCloseable {
    private final long handle;
    private final LoadNativeLib lib;

    public OcrPipeline(long handle, LoadNativeLib lib) {
        this.handle = handle;
        this.lib = lib;
    }

    public long getHandle() { return handle; }

    @Override
    public void close() {
        try {
            lib.ReleaseOcrPipeline(handle);
        } catch (Throwable ignore) {}
    }
}