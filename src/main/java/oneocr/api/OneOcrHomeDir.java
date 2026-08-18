package oneocr.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class OneOcrHomeDir {
    /**
     * Get the user directory where OCR natives and configuration data are stored.
     * This directory is used for extracting native libraries and can be used by tools
     * for storing execution history and other app configuration or user-specific data.
     * 
     * <p>The directory is {@code ~/oneocr}. Before the move to the oneocr organisation it was
     * {@code ~/xyz-jphil/win11_oneocr}; when that one exists and the new one does not it is still
     * used, so an already extracted model and an already downloaded tessdata cache are not orphaned.
     *
     * @return Path to the OneOCR app home directory (~/oneocr)
     */
    public static Path get(){
        var userHome = Paths.get(System.getProperty("user.home"));
        var apphome = userHome.resolve("oneocr");
        if (!Files.exists(apphome)) {
            var legacy = userHome.resolve("xyz-jphil").resolve("win11_oneocr");
            if (Files.isDirectory(legacy)) return legacy;
        }
        return apphome;
    }
    
    public static Path get(boolean createIfNotExists) throws IOException {
        var apphome = get();
        // Create directory if it doesn't exist
        if (!Files.exists(apphome)) {
            Files.createDirectories(apphome);
        }
        return apphome;
    }
    
    public static Optional<Path> findPath(String file){
        // Model file is now managed by ensureNativesExtracted()
        var modelFile = get().resolve(file);
        
        if (Files.exists(modelFile)) {
            return Optional.of(modelFile);
        }
        
        return Optional.empty();
    }
    
}
