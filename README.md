# Windows 11 OneOCR API

Java FFM (Foreign Function & Memory API) wrapper for Windows 11 SnippingTool OCR engine.

## Requirements
- **JDK 22+** (recommended - FFM is final, no preview flags needed)
- **JDK 21** (alternative - FFM is preview, requires `--enable-preview`; for building use `-P build-jdk21`)
- Windows 11 with SnippingTool

## Build
```bash
# JDK 22+ (recommended)
mvn clean package

# JDK 21 (requires preview features)
mvn clean package -P build-jdk21
```

## Usage (API)
```bash
# JDK 22+
java --enable-native-access=oneocr.api -jar target/oneocr-api-2.0.jar

# JDK 21
java --enable-preview --enable-native-access=oneocr.api -jar target/oneocr-api-1.0-jdk21.jar
```

## Testing
```bash
# Performance comparison (auto-detects JDK versions)
java TestJDK21JDK22Plus.java

# Simple test
java --enable-native-access=oneocr.api -cp "target/classes;target/test-classes" oneocr.api.test.SimpleOcrTest
```

## Native Libraries Setup (Development)
For development, extract from `C:\Program Files\WindowsApps\Microsoft.ScreenSketch_11.<version>_x64__8wekyb3d8bbwe\SnippingTool` (e.g., `C:\Program Files\WindowsApps\Microsoft.ScreenSketch_11.2409.25.0_x64__8wekyb3d8bbwe\SnippingTool`):
- `oneocr.dll`
- `oneocr.onemodel` 
- `onnxruntime.dll`
- `opencv_world4120.dll`

Copy to `src/main/resources/natives/` folder.

**Extraction Notes**: `WindowsApps` folder is owned by TrustedInstaller. To extract files:
1. Take ownership temporarily (right-click folder → Properties → Security → Advanced → Change → enter your username)
2. Copy required DLL files to `src/main/resources/natives/`
3. **CRITICAL**: Restore ownership to TrustedInstaller ( **Warning**: Improper ownership changes can break Windows Store apps. See proper reference such as : [WindowsApps Permissions Guide](https://www.winhelponline.com/blog/windowsapps-folder-restore-default-permissions/) )

## Runtime Behavior
At runtime, native libraries are automatically extracted to `<userhome>/oneocr/` and loaded. Built JARs include all dependencies.

**Design**: This API module provides portable, bare-minimum OCR functionality for building custom applications. For ready-to-use command-line tools, see the Tools module.

## Related
- **Tools**: [oneocr-cli](https://github.com/oneocr/cli)
- **Original**: [win11-oneocr](https://github.com/b1tg/win11-oneocr)